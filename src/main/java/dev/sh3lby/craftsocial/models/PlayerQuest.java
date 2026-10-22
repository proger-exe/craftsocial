package dev.sh3lby.craftsocial.models;

import com.google.gson.annotations.SerializedName;

/**
 * Задание (маркетплейс пользовательских квестов).
 * Соответствует PlayerQuestResponse из CraftCoreAPI.
 */
public class PlayerQuest {
    private int id;
    @SerializedName("creator_id")
    private long creatorId;
    private String title;
    private String description;
    private int reward;
    private String status;
    @SerializedName("executor_id")
    private Long executorId;
    @SerializedName("applications_count")
    private int applicationsCount;
    @SerializedName("my_application_status")
    private String myApplicationStatus;
    @SerializedName("submission_message")
    private String submissionMessage;
    @SerializedName("review_comment")
    private String reviewComment;
    @SerializedName("created_at")
    private String createdAt;
    @SerializedName("updated_at")
    private String updatedAt;
    @SerializedName("can_apply")
    private boolean canApply;
    @SerializedName("can_view_applications")
    private boolean canViewApplications;
    @SerializedName("can_cancel")
    private boolean canCancel;
    @SerializedName("can_delete")
    private boolean canDelete;
    @SerializedName("can_submit")
    private boolean canSubmit;
    @SerializedName("can_approve")
    private boolean canApprove;
    @SerializedName("can_request_changes")
    private boolean canRequestChanges;
    @SerializedName("can_abandon")
    private boolean canAbandon;

    public int getId() { return id; }
    public long getCreatorId() { return creatorId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getReward() { return reward; }
    public String getStatus() { return status; }
    public Long getExecutorId() { return executorId; }
    public int getApplicationsCount() { return applicationsCount; }
    public String getMyApplicationStatus() { return myApplicationStatus; }
    public String getSubmissionMessage() { return submissionMessage; }
    public String getReviewComment() { return reviewComment; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public boolean isCanApply() { return canApply; }
    public boolean isCanViewApplications() { return canViewApplications; }
    public boolean isCanCancel() { return canCancel; }
    public boolean isCanDelete() { return canDelete; }
    public boolean isCanSubmit() { return canSubmit; }
    public boolean isCanApprove() { return canApprove; }
    public boolean isCanRequestChanges() { return canRequestChanges; }
    public boolean isCanAbandon() { return canAbandon; }
}
