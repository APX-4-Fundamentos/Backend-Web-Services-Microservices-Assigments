package apx.inc.assignments_service.assignments.interfaces.rest;

import apx.inc.assignments_service.assignments.application.internal.services.external.IamApiService;
import apx.inc.assignments_service.assignments.domain.model.commands.*;
import apx.inc.assignments_service.assignments.domain.model.queries.*;
import apx.inc.assignments_service.assignments.domain.services.SubmissionCommandService;
import apx.inc.assignments_service.assignments.domain.services.SubmissionQueryService;
import apx.inc.assignments_service.assignments.interfaces.rest.resource.CreateSubmissionResource;
import apx.inc.assignments_service.assignments.interfaces.rest.resource.GradeSubmissionResource;
import apx.inc.assignments_service.assignments.interfaces.rest.resource.SubmissionResource;
import apx.inc.assignments_service.assignments.interfaces.rest.resource.UpdateSubmissionResource;
import apx.inc.assignments_service.assignments.interfaces.rest.transform.CreateSubmissionCommandFromResourceAssembler;
import apx.inc.assignments_service.assignments.interfaces.rest.transform.GradeSubmissionCommandFromResourceAssembler;
import apx.inc.assignments_service.assignments.interfaces.rest.transform.SubmissionResourceFromEntityAssembler;
import apx.inc.assignments_service.assignments.interfaces.rest.transform.UpdateSubmissionCommandFromResourceAssembler;
import apx.inc.assignments_service.assignments.application.acl.CloudinaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/api/v1/submissions", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Submissions", description = "Operations related to submissions")
@RequiredArgsConstructor
public class SubmissionsController {

    private final SubmissionCommandService submissionCommandService;
    private final SubmissionQueryService submissionQueryService;
    private final CloudinaryService cloudinaryService;
    private final IamApiService iamApiService;

    @PostMapping
    @Operation(summary = "Create a new submission", description = "Creates a new submission for a challenge by a student.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Submission created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data")
    })
    public ResponseEntity<SubmissionResource> createSubmission(@RequestBody CreateSubmissionResource resource) {
        try {


            Long studentId = iamApiService.getCurrentUser().getId();

            // ✅ USO TUS TRANSFORMS ORIGINALES
            var command = CreateSubmissionCommandFromResourceAssembler.toCommandFromResource(resource, studentId);
            var submissionId = submissionCommandService.handle(command);

            if (submissionId == null || submissionId == 0L) {
                return ResponseEntity.badRequest().build();
            }

            var submission = submissionQueryService.handle(new GetSubmissionByIdQuery(submissionId))
                    .orElseThrow(() -> new RuntimeException("Failed to retrieve created submission"));

            // ✅ USO TUS TRANSFORMS ORIGINALES
            var submissionResponse = SubmissionResourceFromEntityAssembler.toResourceFromEntity(submission);
            return new ResponseEntity<>(submissionResponse, HttpStatus.CREATED);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{submissionId}")
    @Operation(summary = "Update a submission", description = "Update a submission's content by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Submission updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data")
    })
    public ResponseEntity<SubmissionResource> updateSubmission(
            @PathVariable Long submissionId,
            @RequestBody UpdateSubmissionResource resource) {
        try {

            // ✅ USO TUS TRANSFORMS ORIGINALES
            var command = UpdateSubmissionCommandFromResourceAssembler.toUpdateCommandFromResource(submissionId, resource);
            var updatedSubmission = submissionCommandService.handle(command);

            // ✅ USO TUS TRANSFORMS ORIGINALES
            return updatedSubmission.map(s -> ResponseEntity.ok(SubmissionResourceFromEntityAssembler.toResourceFromEntity(s)))
                    .orElse(ResponseEntity.badRequest().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{submissionId}")
    @Operation(summary = "Delete a submission", description = "Deletes a submission by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Submission deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Submission not found")
    })
    public ResponseEntity<Void> deleteSubmission(@PathVariable Long submissionId) {
        try {
            submissionCommandService.handle(new DeleteSubmissionCommand(submissionId));
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{submissionId}")
    @Operation(summary = "Get a submission by ID", description = "Retrieves a submission by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Submission retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Submission not found")
    })
    public ResponseEntity<SubmissionResource> getSubmissionById(@PathVariable Long submissionId) {
        var submission = submissionQueryService.handle(new GetSubmissionByIdQuery(submissionId));
        // ✅ USO TUS TRANSFORMS ORIGINALES
        return submission.map(s -> ResponseEntity.ok(SubmissionResourceFromEntityAssembler.toResourceFromEntity(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Get all submissions", description = "Retrieves all submissions.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Submissions retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "No submissions found")
    })
    public ResponseEntity<List<SubmissionResource>> getAllSubmissions() {
        var submissions = submissionQueryService.handle(new GetAllSubmissionsQuery());
        // ✅ USO TUS TRANSFORMS ORIGINALES
        var resources = submissions.stream()
                .map(SubmissionResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/assignment/{assignmentId}")
    @Operation(summary = "Get submissions by assignmentId", description = "Retrieves submissions by assignmentId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Submissions retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "No submissions found")
    })
    public ResponseEntity<List<SubmissionResource>> getSubmissionsByAssignmentId(@PathVariable Long assignmentId) {
        var submissions = submissionQueryService.handle(new GetSubmissionsByAssignmentIdQuery(assignmentId));
        // ✅ USO TUS TRANSFORMS ORIGINALES
        var resources = submissions.stream()
                .map(SubmissionResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/student/{studentId}")
    @Operation(summary = "Get submissions by studentId", description = "Retrieves submissions submitted by a specific student.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Submissions retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "No submissions found for the given student")
    })
    public ResponseEntity<List<SubmissionResource>> getSubmissionsByStudentId(@PathVariable Long studentId) {
        var submissions = submissionQueryService.handle(new GetSubmissionsByStudentIdQuery(studentId));
        // ✅ USO TUS TRANSFORMS ORIGINALES
        var resources = submissions.stream()
                .map(SubmissionResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/student/{studentId}/assignment/{assignmentId}")
    @Operation(summary = "Get submissions by studentId and assignmentId", description = "Retrieves submissions submitted by a specific student for a specific assignment.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Submissions retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "No submissions found for the given student and assignment")
    })
    public ResponseEntity<List<SubmissionResource>> getSubmissionsByStudentIdAndAssignmentId(
            @PathVariable Long studentId,
            @PathVariable Long assignmentId) {
        var submissions = submissionQueryService.handle(new GetSubmissionsByStudentIdAndAssignmentIdQuery(studentId, assignmentId));
        // ✅ USO TUS TRANSFORMS ORIGINALES
        var resources = submissions.stream()
                .map(SubmissionResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/student/{studentId}/course/{courseId}")
    @Operation(summary = "Get submissions by studentId and courseId", description = "Retrieves submissions submitted by a specific student in a specific course.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Submissions retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "404", description = "No submissions found for the given student and course")
    })
    public ResponseEntity<List<SubmissionResource>> getSubmissionsByStudentIdAndCourseId(
            @PathVariable Long studentId,
            @PathVariable Long courseId) {
        try {
            var submissions = submissionQueryService.handle(new GetSubmissionsByStudentIdAndCourseIdQuery(studentId, courseId));
            // ✅ USO TUS TRANSFORMS ORIGINALES
            var resources = submissions.stream()
                    .map(SubmissionResourceFromEntityAssembler::toResourceFromEntity)
                    .toList();
            return ResponseEntity.ok(resources);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/course/{courseId}")
    @Operation(summary = "Get submissions by courseId", description = "Retrieves submissions submitted in a specific course.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Submissions retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "404", description = "No submissions found for the given course")
    })
    public ResponseEntity<List<SubmissionResource>> getSubmissionsByCourseId(@PathVariable Long courseId) {
        try {
            var submissions = submissionQueryService.handle(new GetSubmissionsByCourseIdQuery(courseId));
            // ✅ USO TUS TRANSFORMS ORIGINALES
            var resources = submissions.stream()
                    .map(SubmissionResourceFromEntityAssembler::toResourceFromEntity)
                    .toList();
            return ResponseEntity.ok(resources);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{submissionId}/grade")
    @Operation(summary = "Grade a submission", description = "Updates the score of a submission.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Submission graded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data")
    })
    public ResponseEntity<SubmissionResource> gradeSubmission(
            @PathVariable Long submissionId,
            @RequestBody GradeSubmissionResource resource) {
        try {
            // ✅ USO TUS TRANSFORMS ORIGINALES
            var command = GradeSubmissionCommandFromResourceAssembler.toCommandFromResource(submissionId, resource);
            var gradedSubmission = submissionCommandService.handle(command);

            // ✅ USO TUS TRANSFORMS ORIGINALES
            return gradedSubmission.map(s -> ResponseEntity.ok(SubmissionResourceFromEntityAssembler.toResourceFromEntity(s)))
                    .orElse(ResponseEntity.badRequest().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping(value = "/{submissionId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Add files to submission")
    public ResponseEntity<List<String>> addFilesToSubmission(
            @Parameter(description = "ID of the submission", required = true)
            @PathVariable Long submissionId,

            @Parameter(description = "Files to upload", required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "string", format = "binary")))
            @RequestPart("files") MultipartFile[] files) {

        try {
            List<String> uploadedUrls = new ArrayList<>();

            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    String fileUrl = cloudinaryService.uploadFile(file);
                    uploadedUrls.add(fileUrl);
                }
            }

            var command = new AddFilesToSubmissionCommand(submissionId, uploadedUrls);
            submissionCommandService.handle(command);

            return ResponseEntity.ok(uploadedUrls);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{submissionId}/files")
    @Operation(summary = "Remove file from submission")
    public ResponseEntity<Void> removeFileFromSubmission(
            @PathVariable Long submissionId,
            @RequestParam String fileUrl) {
        try {
            var command = new RemoveFileFromSubmissionCommand(submissionId, fileUrl);
            submissionCommandService.handle(command);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{submissionId}/files")
    @Operation(summary = "Get all files from a submission", description = "Retrieves all file URLs uploaded to a specific submission.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Files retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Submission not found or has no files")
    })
    public ResponseEntity<List<String>> getFilesBySubmissionId(@PathVariable Long submissionId) {
        try {
            var files = submissionQueryService.handle(new GetFilesBySubmissionIdQuery(submissionId));
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}