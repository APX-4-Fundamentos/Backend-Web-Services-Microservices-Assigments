package apx.inc.assignments_service.assignments.domain.model.commands;

public record UpdateSubmissionCommand(
        Long submissionId,
        Long assignmentId,
        String content
) {

    public UpdateSubmissionCommand {
        if (submissionId == null || submissionId <= 0) {
            throw new IllegalArgumentException("SubmissionId must be greater than 0");
        }
        if (assignmentId == null || assignmentId <= 0) {
            throw new IllegalArgumentException("Challenge ID must be greater than 0");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Content cannot be null or blank");
        }
    }
}
