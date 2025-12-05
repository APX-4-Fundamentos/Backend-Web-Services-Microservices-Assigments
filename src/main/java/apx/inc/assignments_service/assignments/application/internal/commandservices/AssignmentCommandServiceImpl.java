package apx.inc.assignments_service.assignments.application.internal.commandservices;


import apx.inc.assignments_service.assignments.application.internal.services.external.CoursesApiService;
import apx.inc.assignments_service.assignments.application.internal.services.external.IamApiService;
import apx.inc.assignments_service.assignments.domain.model.aggregates.Assignment;
import apx.inc.assignments_service.assignments.domain.model.commands.*;
import apx.inc.assignments_service.assignments.domain.services.AssignmentCommandService;
import apx.inc.assignments_service.assignments.infrastructure.authotization.sfs.services.AuthenticationService;
import apx.inc.assignments_service.assignments.infrastructure.persistence.jpa.repositories.AssignmentRepository;
import apx.inc.assignments_service.assignments.infrastructure.persistence.jpa.repositories.SubmissionRepository;
import apx.inc.assignments_service.assignments.application.acl.CloudinaryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Optional;

@Service
public class AssignmentCommandServiceImpl implements AssignmentCommandService {

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final CloudinaryService cloudinaryService;
    private final CoursesApiService coursesApiService;
    private final AuthenticationService authenticationService;

    public AssignmentCommandServiceImpl(AssignmentRepository assignmentRepository, SubmissionRepository submissionRepository, CloudinaryService cloudinaryService, CoursesApiService coursesApiService, IamApiService iamApiService, AuthenticationService authenticationService) {
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.cloudinaryService=cloudinaryService;
        this.coursesApiService = coursesApiService;
        this.authenticationService = authenticationService;
    }

    private String getCurrentAuthHeader() {
        try {
            var requestAttributes = RequestContextHolder.getRequestAttributes();
            if (requestAttributes instanceof ServletRequestAttributes) {
                HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    return authHeader;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Cannot get authentication token from request", e);
        }
        throw new RuntimeException("No authentication token found");
    }

    @Override
    public Long handle(CreateAssignmentCommand command) {
        // Validar que el curso existe
        if (!coursesApiService.courseExists(command.courseId())) {
            throw new IllegalArgumentException("Course not found: " + command.courseId());
        }

        // Validar que el usuario autenticado es el profesor del curso
        String authHeader = getCurrentAuthHeader();
        Long authenticatedUserId = authenticationService.getAuthenticatedUserId(authHeader);

        if (!coursesApiService.isCourseTeacher(authenticatedUserId, command.courseId())) {
            throw new IllegalArgumentException("Only the course teacher can create assignments");
        }

        // Validar título único en el curso
        if (assignmentRepository.existsByTitleAndCourseId(command.title(), command.courseId())) {
            throw new IllegalArgumentException("An assignment with this title already exists in the course");
        }

        var assignment = new Assignment(command);
        try {
            assignmentRepository.save(assignment);
            return assignment.getId();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to create assignment: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Assignment> handle(UpdateAssignmentCommand command) {
        var assignment = assignmentRepository.findById(command.assigmentId());
        if (assignment.isEmpty()) {
            throw new IllegalArgumentException("Assignment not found: " + command.assigmentId());
        }

        // Validar permisos - solo el profesor del curso puede actualizar
        String authHeader = getCurrentAuthHeader();
        Long authenticatedUserId = authenticationService.getAuthenticatedUserId(authHeader);

        Assignment existingAssignment = assignment.get();
        if (!coursesApiService.isCourseTeacher(authenticatedUserId, existingAssignment.getCourseId())) {
            throw new IllegalArgumentException("Only the course teacher can update assignments");
        }

        // Validar que el nuevo curso existe (si se cambia el curso)
        if (!command.courseId().equals(existingAssignment.getCourseId())) {
            if (!coursesApiService.courseExists(command.courseId())) {
                throw new IllegalArgumentException("New course not found: " + command.courseId());
            }
            // Validar que el profesor también es profesor del nuevo curso
            if (!coursesApiService.isCourseTeacher(authenticatedUserId, command.courseId())) {
                throw new IllegalArgumentException("You are not the teacher of the new course");
            }
        }

        var existingAssignmentWithSameTitle = assignmentRepository.findByTitleAndCourseId(
                command.title(), command.courseId()
        );

        if (existingAssignmentWithSameTitle.isPresent() &&
                !existingAssignmentWithSameTitle.get().getId().equals(command.assigmentId())) {
            throw new IllegalArgumentException("An assignment with this title already exists in the course");
        }

        var assignmentToUpdate = assignment.get();
        try {
            var updatedAssignment = assignmentRepository.save(assignmentToUpdate
                    .updateInformation(
                            command.title(),
                            command.description(),
                            command.courseId(),
                            command.deadline(),
                            command.imageUrl()
                    ));
            return Optional.of(updatedAssignment);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to update assignment: " + e.getMessage(), e);
        }
    }

    @Override
    public void handle(DeleteAssignmentCommand command) {
        var assignment = assignmentRepository.findById(command.assigmentId())
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + command.assigmentId()));

        // Validar permisos - solo el profesor del curso puede eliminar
        String authHeader = getCurrentAuthHeader();
        Long authenticatedUserId = authenticationService.getAuthenticatedUserId(authHeader);

        if (!coursesApiService.isCourseTeacher(authenticatedUserId, assignment.getCourseId())) {
            throw new IllegalArgumentException("Only the course teacher can delete assignments");
        }

        try {
            // 1. Eliminar todos los submissions asociados al assignment
            var submissions = submissionRepository.findByAssignmentId(command.assigmentId());
            if (!submissions.isEmpty()) {
                submissionRepository.deleteAll(submissions);
                System.out.println("🗑️ Deleted " + submissions.size() + " submissions for assignment " + command.assigmentId());
            }

            // 2. Eliminar todos los archivos de Cloudinary
            List<String> fileUrls = assignment.getFileUrls();
            if (fileUrls != null && !fileUrls.isEmpty()) {
                for (String fileUrl : fileUrls) {
                    try {
                        cloudinaryService.deleteFile(fileUrl);
                        System.out.println("🗑️ Deleted file from Cloudinary: " + fileUrl);
                    } catch (Exception e) {
                        System.out.println("⚠️ Failed to delete file from Cloudinary: " + fileUrl + " - " + e.getMessage());
                        // Continuar con la eliminación aunque falle un archivo
                    }
                }
            }

            // 3. Finalmente eliminar el assignment
            assignmentRepository.delete(assignment);
            System.out.println("✅ Assignment " + command.assigmentId() + " deleted successfully");

        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to delete assignment: " + e.getMessage(), e);
        }
    }

    @Override
    public void handle(AddFilesToAssignmentCommand command) {
        Assignment assignment = assignmentRepository.findById(command.assignmentId())
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found with ID: " + command.assignmentId()));

        // Validar permisos
        String authHeader = getCurrentAuthHeader();
        Long authenticatedUserId = authenticationService.getAuthenticatedUserId(authHeader);

        if (!coursesApiService.isCourseTeacher(authenticatedUserId, assignment.getCourseId())) {
            throw new IllegalArgumentException("Only the course teacher can add files to assignments");
        }

        for (String fileUrl : command.fileUrls()) {
            assignment.addFileUrl(fileUrl);
        }

        try {
            assignmentRepository.save(assignment);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to add files to assignment: " + e.getMessage(), e);
        }
    }

    @Override
    public void handle(RemoveFileFromAssignmentCommand command) {
        Assignment assignment = assignmentRepository.findById(command.assignmentId())
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found with ID: " + command.assignmentId()));

        // Validar permisos
        String authHeader = getCurrentAuthHeader();
        Long authenticatedUserId = authenticationService.getAuthenticatedUserId(authHeader);

        if (!coursesApiService.isCourseTeacher(authenticatedUserId, assignment.getCourseId())) {
            throw new IllegalArgumentException("Only the course teacher can remove files from assignments");
        }

        assignment.removeFileUrl(command.fileUrl());

        try {
            assignmentRepository.save(assignment);
            cloudinaryService.deleteFile(command.fileUrl());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to remove file from assignment: " + e.getMessage(), e);
        }
    }


}
