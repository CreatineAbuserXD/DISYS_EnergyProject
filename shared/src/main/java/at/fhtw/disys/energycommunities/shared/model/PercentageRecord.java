package at.fhtw.disys.energycommunities.shared.model;

import java.time.LocalDateTime;

public class PercentageRecord {

    private LocalDateTime bucketHour;
    private double communityDepleted;
    private double gridPortion;

    public PercentageRecord() {
    }

    public LocalDateTime getBucketHour() {
        return bucketHour;
    }

    public void setBucketHour(LocalDateTime bucketHour) {
        this.bucketHour = bucketHour;
    }

    public double getCommunityDepleted() {
        return communityDepleted;
    }

    public void setCommunityDepleted(double communityDepleted) {
        this.communityDepleted = communityDepleted;
    }

    public double getGridPortion() {
        return gridPortion;
    }

    public void setGridPortion(double gridPortion) {
        this.gridPortion = gridPortion;
    }
}
