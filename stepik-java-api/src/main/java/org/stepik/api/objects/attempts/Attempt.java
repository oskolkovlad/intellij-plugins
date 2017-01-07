package org.stepik.api.objects.attempts;

import com.google.gson.annotations.SerializedName;

/**
 * @author meanmail
 */
public class Attempt {
    private int id;
    private String dataset;
    @SerializedName("dataset_url")
    private String datasetUrl;
    private String time;
    private String status;
    @SerializedName("time_left")
    private int timeLeft;
    private int step;
    private int user;

    public int getId() {
        return id;
    }

    public void setStep(int step) {
        this.step = step;
    }
}