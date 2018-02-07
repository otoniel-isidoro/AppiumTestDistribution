package com.appium.manager;

import com.appium.entities.MobilePlatform;
import com.appium.utils.ScreenShotManager;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.Status;
import com.video.recorder.Flick;
import gherkin.formatter.model.Result;
import io.appium.java_client.remote.AndroidMobileCapabilityType;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.logging.LogEntry;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class CucumberLogger {
    private Flick videoRecording;
    public File logFile;
    private List<LogEntry> logEntries;
    private PrintWriter log_file_writer;
    private ScreenShotManager screenShotManager;
    private String videoPath;

    public String getVideoPath() {
        return videoPath;
    }

    public void setVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }

    public CucumberLogger() {
        this.videoRecording = new Flick();
        screenShotManager = new ScreenShotManager();
    }

    public void startLogging(String methodName, String className, String deviceModel) throws FileNotFoundException {
        Capabilities capabilities = AppiumDriverManager.getDriver().getCapabilities();
        if (AppiumDeviceManager.getMobilePlatform().equals(MobilePlatform.ANDROID)) {
            if (capabilities.getCapability("browserName") == null) {
                System.out.println("Starting ADB logs" + AppiumDeviceManager.getDeviceUDID());
                logEntries = AppiumDriverManager.getDriver().manage()
                        .logs().get("logcat").getAll();
                if (ConfigFileManager.getInstance().getProperty("FILTER_LOGCAT_BY_APP_PACKAGE") != null &&
                        ConfigFileManager.getInstance().getProperty("FILTER_LOGCAT_BY_APP_PACKAGE").equalsIgnoreCase("true")) {
                    logEntries = logEntries.stream().filter(logEntry -> logEntry.toString().contains(
                            capabilities.getCapability(AndroidMobileCapabilityType.APP_PACKAGE).toString())
                    ).collect(Collectors.toList());
                }
                logFile = new File(System.getProperty("user.dir") + "/target/adblogs/"
                        + AppiumDeviceManager.getDeviceUDID()
                        + "_" + deviceModel + "_" + methodName + ".txt");
                log_file_writer = new PrintWriter(logFile);
            }
        }
        startVideoRecording(methodName,
                className, deviceModel);
    }


    private void startVideoRecording(String methodName, String className, String deviceModel) {
        if (System.getenv("VIDEO_LOGS") != null) {
            try {
                videoRecording
                        .startVideoRecording(className,
                                methodName, deviceModel);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public HashMap<String, String> endLog(String featureName, String scenarioName, Result result, String deviceModel,
                                          ExtentTest test)
            throws IOException, InterruptedException {
        HashMap<String, String> logs = new HashMap<>();
        String className = featureName;
        stopViewRecording(scenarioName, result, className, deviceModel);
        String adbPath = "adblogs/"
                + AppiumDeviceManager.getDeviceUDID()
                + "_" + deviceModel + "_"
                + scenarioName
                + ".txt";
        logs.put("adbLogs", adbPath);

        getAdbLogs(adbPath, test);
        if (System.getenv("VIDEO_LOGS") != null) {
            setVideoPath("screenshot/" + AppiumDeviceManager.getMobilePlatform()
                    .toString().toLowerCase()
                    + "/" + AppiumDeviceManager.getDeviceUDID() + "/" + deviceModel
                    + "/" + className + "/" + scenarioName
                    + "/" + deviceModel + ".mp4");
            logs.put("videoLogs", getVideoPath());
            if (new File(System.getProperty("user.dir") + "/target/" + getVideoPath()).exists()) {
                test.info("<h4 class='md-display-4'>" +
                        "Video Log:</h4><video width=\"320\" height=\"568\" controls>\n" +
                        "  <source src=\"" + getVideoPath() + "\" style=\"width:100%;height:100%;\" type=\"video/mp4\">\n" +
                        "Your browser does not support the video tag.\n" +
                        "</video>");
            }
        }
        return logs;
    }

    private void stopViewRecording(String scenarioName, Result result, String className, String deviceModel)
            throws IOException, InterruptedException {
        if (System.getenv("VIDEO_LOGS") != null) {
            try {
                videoRecording.stopVideoRecording(className,
                        scenarioName,
                        deviceModel);
            } catch (Exception e) {
                System.err.println(e);
            }
        }
        if (ConfigFileManager.getInstance().getProperty("KEEP_SUCCESS_VIDEOS") == null ||
                ConfigFileManager.getInstance().getProperty("KEEP_SUCCESS_VIDEOS").equalsIgnoreCase("false")) {
            deleteSuccessVideos(scenarioName, result, className, deviceModel);
        }
    }

    private void deleteSuccessVideos(String scenarioName, Result result, String className, String deviceModel) {
        if (result.getStatus().equalsIgnoreCase("passed")) {
            File videoFile = new File(System.getProperty("user.dir")
                    + "/target/screenshot/android/"
                    + AppiumDeviceManager.getDeviceUDID() + "/" + deviceModel + "/"
                    + className + "/" + scenarioName
                    + "/" + deviceModel + ".mp4");
            if (videoFile.exists()) {
                videoFile.delete();
            }
        }
    }


    public void getAdbLogs(String adbPath,
                           ExtentTest test) {
        if (AppiumDeviceManager.getMobilePlatform().equals(MobilePlatform.ANDROID)
                && AppiumDriverManager.getDriver().getCapabilities()
                .getCapability("browserName") == null) {
            log_file_writer.println(logEntries);
            log_file_writer.close();
            test.log(Status.INFO,
                    "<h4 class='md-display-4'>Logcat:</h4><br/><object data=\"" + adbPath + "\" type=\"text/plain\"" +
                            "width=\"500\" style=\"width:100%;height:100%;\">" +
                            "<a href=\"" + adbPath + "\">Logcat</a>\n" +
                            "</object>"
            );
            System.out.println(AppiumDriverManager.getDriver()
                    .getSessionId() + ": Saving device log - Done.");
        }
    }

}
