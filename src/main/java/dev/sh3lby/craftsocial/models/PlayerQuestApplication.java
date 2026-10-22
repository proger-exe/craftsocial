package dev.sh3lby.craftsocial.models;

import com.google.gson.annotations.SerializedName;

/**
 * Отклик исполнителя на задание.
 * Соответствует PlayerQuestApplicationResponse из CraftCoreAPI.
 */
public class PlayerQuestApplication {
    private int id;
    @SerializedName("quest_id")
    private int questId;
    @SerializedName("applicant_id")
    private long applicantId;
    private String status;
    @SerializedName("cover_letter")
    private String coverLetter;
    @SerializedName("submission_message")
    private String submissionMessage;
    @SerializedName("review_comment")
    private String reviewComment;
    @SerializedName("created_at")
    private String createdAt;
    @SerializedName("updated_at")
    private String updatedAt;
    @SerializedName("quest_title")
    private String questTitle;

    public int getId() { return id; }
    public int getQuestId() { return questId; }
    public long getApplicantId() { return applicantId; }
    public String getStatus() { return status; }
    public String getCoverLetter() { return coverLetter; }
    public String getSubmissionMessage() { return submissionMessage; }
    public String getReviewComment() { return reviewComment; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public String getQuestTitle() { return questTitle; }
}
