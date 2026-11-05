package desk.model;

import java.time.LocalDateTime;

/** Una empresa/entrevista: corresponde a una carpeta dentro de "Interviewer Specialist". */
public record Company(
        long id,
        String name,
        String folder,
        String meetingLink,
        String jobDescription,
        String resumePath,
        LocalDateTime addedAt,
        boolean hidden) {

    public boolean hasLink() {
        return meetingLink != null && !meetingLink.isBlank();
    }
}
