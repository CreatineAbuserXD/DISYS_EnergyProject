package at.fhtw.disys.energycommunities.shared.model;

import java.time.LocalDateTime;

public class UsageBucket {

    private long id;
    private LocalDateTime bucketHour;
    private double communityProduced;
    private double communityUsed;
    private double gridUsed;

    public UsageBucket() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public LocalDateTime getBucketHour() {
        return bucketHour;
    }

    public void setBucketHour(LocalDateTime bucketHour) {
        this.bucketHour = bucketHour;
    }

    public double getCommunityProduced() {
        return communityProduced;
    }

    public void setCommunityProduced(double communityProduced) {
        this.communityProduced = communityProduced;
    }

    public double getCommunityUsed() {
        return communityUsed;
    }

    public void setCommunityUsed(double communityUsed) {
        this.communityUsed = communityUsed;
    }

    public double getGridUsed() {
        return gridUsed;
    }

    public void setGridUsed(double gridUsed) {
        this.gridUsed = gridUsed;
    }
}
