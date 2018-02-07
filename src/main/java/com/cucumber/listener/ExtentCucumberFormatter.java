package com.cucumber.listener;

import com.appium.android.AndroidDeviceConfiguration;
import com.appium.entities.MobilePlatform;
import com.appium.ios.IOSDeviceConfiguration;
import com.appium.manager.*;
import com.appium.utils.ImageUtils;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.GherkinKeyword;
import com.aventstack.extentreports.markuputils.Markup;
import com.aventstack.extentreports.markuputils.MarkupHelper;
import com.report.factory.ExtentManager;
import com.video.recorder.XpathXML;
import gherkin.formatter.Formatter;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.*;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.MobileElement;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.Connection;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.im4java.core.IM4JavaException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.html5.Location;
import org.testng.ISuite;
import org.testng.ISuiteListener;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


/**
 * Cucumber custom format listener which generates ExtentsReport html file
 */
public class ExtentCucumberFormatter implements Reporter, Formatter, ISuiteListener {

    private final DeviceAllocationManager deviceAllocationManager;
    public AppiumServerManager appiumServerManager;
    public AppiumDriverManager appiumDriverManager;
    public DeviceSingleton deviceSingleton;
    public AppiumDriver<MobileElement> appium_driver;
    private AndroidDeviceConfiguration androidDevice;
    private IOSDeviceConfiguration iosDevice;
    public String deviceModel;
    public ImageUtils imageUtils = new ImageUtils();
    public XpathXML xpathXML = new XpathXML();
    private ConfigFileManager prop;
    private String CI_BASE_URI = null;
    private Feature feature;
    private CucumberLogger testLogger;
    private Result result;
    private static ThreadLocal<ExtentTest> featureTestThreadLocal = new InheritableThreadLocal<>();
    private static ThreadLocal<ExtentTest> scenarioOutlineThreadLocal = new InheritableThreadLocal<>();
    static ThreadLocal<ExtentTest> scenarioThreadLocal = new InheritableThreadLocal<>();
    private static ThreadLocal<LinkedList<Step>> stepListThreadLocal =
            new InheritableThreadLocal<>();
    static ThreadLocal<ExtentTest> stepTestThreadLocal = new InheritableThreadLocal<>();
    private boolean scenarioOutlineFlag;

    private static final Map<String, String> MIME_TYPES_EXTENSIONS = new HashMap() {
        {
            this.put("image/bmp", "bmp");
            this.put("image/gif", "gif");
            this.put("image/jpeg", "jpg");
            this.put("image/png", "png");
            this.put("image/svg+xml", "svg");
            this.put("video/ogg", "ogg");
        }
    };

    public ExtentCucumberFormatter() {
        deviceAllocationManager = DeviceAllocationManager.getInstance();
        try {
            deviceSingleton = DeviceSingleton.getInstance();
            appiumDriverManager = new AppiumDriverManager();
            appiumServerManager = new AppiumServerManager();
            iosDevice = new IOSDeviceConfiguration();
            androidDevice = new AndroidDeviceConfiguration();
            prop = ConfigFileManager.getInstance();
            testLogger = new CucumberLogger();
            stepListThreadLocal.set(new LinkedList<>());
            scenarioOutlineFlag = false;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void before(Match match, Result result) {
    }

    public void result(Result result) {
        if (scenarioOutlineFlag) {
            return;
        }
        this.result = result;
        if (Result.PASSED.equals(result.getStatus())) {
            stepTestThreadLocal.get().pass(Result.PASSED);
        } else if (Result.FAILED.equals(result.getStatus())) {
            String failed_StepName = stepListThreadLocal.get().getFirst().getName();
            stepTestThreadLocal.get().fail(result.getError());
            String context = AppiumDriverManager.getDriver().getContext();
            boolean contextChanged = false;
            if ("Android".equalsIgnoreCase(AppiumDriverManager.getDriver()
                    .getSessionDetails().get("platform")
                    .toString())
                    && !"NATIVE_APP".equals(context)) {
                AppiumDriverManager.getDriver().context("NATIVE_APP");
                contextChanged = true;
            }
            File scrFile = ((TakesScreenshot) AppiumDriverManager.getDriver())
                    .getScreenshotAs(OutputType.FILE);
            if (contextChanged) {
                AppiumDriverManager.getDriver().context(context);
            }
            if (AppiumDeviceManager.getMobilePlatform().equals(MobilePlatform.ANDROID)) {
                screenShotAndFrame(failed_StepName, scrFile, "android");
            } else if (AppiumDeviceManager.getMobilePlatform().equals(MobilePlatform.IOS)) {
                screenShotAndFrame(failed_StepName, scrFile, "iPhone");
            }
            try {
                attachScreenShotToReport(failed_StepName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (Result.SKIPPED.equals(result)) {
            stepTestThreadLocal.get().skip(Result.SKIPPED.getStatus());
        } else if (Result.UNDEFINED.equals(result)) {
            stepTestThreadLocal.get().skip(Result.UNDEFINED.getStatus());
        }
    }

    public void after(Match match, Result result) {
    }

    public void match(Match match) {
        Step step = stepListThreadLocal.get().poll();
        String data[][] = null;
        if (step.getRows() != null) {
            List<DataTableRow> rows = step.getRows();
            int rowSize = rows.size();
            for (int i = 0; i < rowSize; i++) {
                DataTableRow dataTableRow = rows.get(i);
                List<String> cells = dataTableRow.getCells();
                int cellSize = cells.size();
                if (data == null) {
                    data = new String[rowSize][cellSize];
                }
                for (int j = 0; j < cellSize; j++) {
                    data[i][j] = cells.get(j);
                }
            }
        }

        ExtentTest scenarioTest = scenarioThreadLocal.get();
        ExtentTest stepTest = null;

        try {
            stepTest = scenarioTest.createNode(new GherkinKeyword(step.getKeyword()), step.getKeyword() + step.getName());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        if (data != null) {
            Markup table = MarkupHelper.createTable(data);
            stepTest.info(table);
        }
        stepTestThreadLocal.set(stepTest);
    }

    public void embedding(String s, byte[] bytes) {
    }

    public void write(String s) {
        // ReportManager.endTest(parent);
    }

    public void syntaxError(String s, String s1, List<String> list, String s2, Integer integer) {

    }

    public void uri(String s) {

    }

    public void feature(Feature feature) {
        this.feature = feature;
        String[] tagsArray = getTagArray(feature.getTags());
        String tags = String.join(",", tagsArray);
        if (prop.getProperty("RUNNER").equalsIgnoreCase("parallel")) {
            deviceAllocationManager.getNextAvailableDeviceId();
            String[] deviceThreadNumber = Thread.currentThread().getName().toString().split("_");
            System.out.println(deviceThreadNumber);
            System.out.println("Feature Tag Name::" + feature.getTags());
            try {

                if (prop.getProperty("CI_BASE_URI") != null) {
                    CI_BASE_URI = prop.getProperty("CI_BASE_URI").toString().trim();
                } else if (CI_BASE_URI == null || CI_BASE_URI.isEmpty()) {
                    CI_BASE_URI = System.getProperty("user.dir");
                }
                String device = xpathXML.parseXML(Integer
                        .parseInt(deviceThreadNumber[1]));
                deviceAllocationManager.allocateDevice(
                        device,
                        AppiumDeviceManager.getDeviceUDID());
                if (AppiumDeviceManager.getDeviceUDID() == null) {
                    System.out.println("No devices are free to run test "
                            + "or Failed to run childTest");
                }
                featureTestThreadLocal.set(ExtentManager.getExtent()
                        .createTest(com.aventstack.extentreports.gherkin.model.Feature.class, feature.getName(),
                                "").assignCategory(tags));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            try {
                deviceAllocationManager.allocateDevice("",
                        deviceSingleton.getDeviceUDID());
                featureTestThreadLocal.set(ExtentManager.getExtent()
                        .createTest(com.aventstack.extentreports.gherkin.model.Feature.class, feature.getName(),
                                "").assignCategory(tags));
                //appiumServerManager.startAppiumServer();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String[] getTagArray(List<Tag> tags) {
        String[] tagArray = new String[tags.size()];
        for (int i = 0; i < tags.size(); i++) {
            tagArray[i] = tags.get(i).getName();
        }
        return tagArray;
    }

    public void scenarioOutline(ScenarioOutline scenarioOutline) {
        scenarioOutlineFlag = true;
        ExtentTest node = featureTestThreadLocal.get()
                .createNode(com.aventstack.extentreports.gherkin.model.ScenarioOutline.class, scenarioOutline.getName());
        scenarioOutlineThreadLocal.set(node);
    }

    public void examples(Examples examples) {
        ExtentTest test = scenarioOutlineThreadLocal.get();

        String[][] data = null;
        List<ExamplesTableRow> rows = examples.getRows();
        int rowSize = rows.size();
        for (int i = 0; i < rowSize; i++) {
            ExamplesTableRow examplesTableRow = rows.get(i);
            List<String> cells = examplesTableRow.getCells();
            int cellSize = cells.size();
            if (data == null) {
                data = new String[rowSize][cellSize];
            }
            for (int j = 0; j < cellSize; j++) {
                data[i][j] = cells.get(j);
            }
        }
        test.info(MarkupHelper.createTable(data));
    }

    public void startOfScenarioLifeCycle(Scenario scenario) {
        createAppiumInstance(scenario);
        if (scenarioOutlineFlag) {
            scenarioOutlineFlag = false;
        }
        try {
            if (AppiumDeviceManager.getMobilePlatform().equals(MobilePlatform.ANDROID)) {
                deviceModel = androidDevice.getDeviceModel();
            } else if (AppiumDeviceManager.getMobilePlatform().equals(MobilePlatform.IOS)) {
                try {
                    deviceModel =
                            iosDevice.getIOSDeviceProductTypeAndVersion();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            testLogger.startLogging(sanetizeString(scenario.getName()),
                    sanetizeString(this.feature.getName()),
                    sanetizeString(deviceModel));
        } catch (Exception e) {
            e.printStackTrace();
        }

        ExtentTest scenarioNode;
        if (scenarioOutlineThreadLocal.get() != null && scenario.getKeyword().trim()
                .equalsIgnoreCase("Scenario Outline")) {
            scenarioNode = scenarioOutlineThreadLocal.get()
                            .createNode(com.aventstack.extentreports.gherkin.model.Scenario.class, scenario.getName())
                            .assignCategory(deviceModel, AppiumDeviceManager.getDeviceUDID());
        } else {
            scenarioNode = featureTestThreadLocal.get()
                    .createNode(com.aventstack.extentreports.gherkin.model.Scenario.class, scenario.getName())
                    .assignCategory(deviceModel, AppiumDeviceManager.getDeviceUDID());
        }

        for (Tag tag : scenario.getTags()) {
            scenarioNode.assignCategory(tag.getName());
        }
        scenarioThreadLocal.set(scenarioNode);
    }

    public void createAppiumInstance(Scenario scenario) {
        String[] tagsArray = getTagArray(scenario.getTags());
        String tags = String.join(",", tagsArray);
        try {
            startAppiumServer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //TO DO fix this
    public void startAppiumServer() throws Exception {
        appiumDriverManager.startAppiumDriverInstance();
        ///This portion should be Broken : TODO
    }

    public void background(Background background) {

    }

    public void scenario(Scenario scenario) {

    }

    public void step(Step step) {
        if (scenarioOutlineFlag) {
            return;
        }
        stepListThreadLocal.get().add(step);

    }

    public void endOfScenarioLifeCycle(Scenario scenario) {
        try {
            testLogger.endLog(sanetizeString(feature.getName()),
                    sanetizeString(scenario.getName()),
                    result, sanetizeString(deviceModel),
                    stepTestThreadLocal.get());
        } catch (Exception e) {
            e.printStackTrace();
        }
        ExtentManager.getExtent().flush();
        AppiumDriverManager.getDriver().quit();
    }

    public String sanetizeString(String string) {
        return StringUtils.stripAccents(string).replaceAll(" ", "_");
    }

    public void done() {

    }

    public void close() {

    }

    public void eof() {
        ExtentManager.getExtent().flush();
        try {
            deviceAllocationManager.freeDevice();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void screenShotAndFrame(String failed_StepName, File scrFile, String device) {
        try {
            File framePath =
                    new File(System.getProperty("user.dir") + "/src/test/resources/frames/");
            FileUtils.copyFile(scrFile, new File(
                    System.getProperty("user.dir") + "/target/screenshot/" + device + "/"
                            + AppiumDeviceManager.getDeviceUDID()
                            + "/" + sanetizeString(deviceModel)
                            + "/failed_" + sanetizeString(failed_StepName) + ".jpeg"));
            File[] files1 = framePath.listFiles();
            if (framePath.exists()) {
                for (int i = 0; i < files1.length; i++) {
                    if (files1[i].isFile()) { //this line weeds out other directories/folders
                        Path p = Paths.get(files1[i].toString());
                        String fileName = p.getFileName().toString().toLowerCase();
                        if (deviceModel.toString().toLowerCase()
                                .contains(fileName.split(".png")[0].toLowerCase())) {
                            try {
                                imageUtils.wrapDeviceFrames(
                                        files1[i].toString(),
                                        System.getProperty("user.dir")
                                                + "/target/screenshot/" + device
                                                + "/" + AppiumDeviceManager.getDeviceUDID()
                                                .replaceAll("\\W", "_") + "/"
                                                + sanetizeString(deviceModel) + "/failed_"
                                                + sanetizeString(failed_StepName) + ".jpeg",
                                        System.getProperty("user.dir")
                                                + "/target/screenshot/" + device
                                                + "/" + AppiumDeviceManager.getDeviceUDID()
                                                .replaceAll("\\W", "_") + "/"
                                                + sanetizeString(deviceModel) + "/failed_"
                                                + sanetizeString(failed_StepName)
                                                + "_framed.jpeg");
                                break;
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            } catch (IM4JavaException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            System.out.println("Resource Directory was not found");
        }
    }

    public void attachScreenShotToReport(String stepName) throws IOException {
        String platform = null;
        if (AppiumDeviceManager.getMobilePlatform().equals(MobilePlatform.ANDROID)) {
            platform = "android";
        } else if (AppiumDeviceManager.getMobilePlatform().equals(MobilePlatform.IOS)) {
            platform = "iPhone";
        }
        File framedImageAndroid = new File(
                System.getProperty("user.dir") + "/target/screenshot/" + platform + "/"
                        + AppiumDeviceManager.getDeviceUDID() + "/" + sanetizeString(deviceModel)
                        + "/failed_" + sanetizeString(stepName) + "_framed.jpeg");
        if (framedImageAndroid.exists()) {
            stepTestThreadLocal.get().addScreenCaptureFromPath(
                    "screenshot/"
                            + platform + "/"
                            + AppiumDeviceManager.getDeviceUDID()
                            + "/" + sanetizeString(deviceModel)
                            + "/failed_" + sanetizeString(stepName) + "_framed.jpeg");
        } else {
            stepTestThreadLocal.get().addScreenCaptureFromPath("screenshot/"
                    + platform + "/"
                    + AppiumDeviceManager.getDeviceUDID()
                    + "/" + sanetizeString(deviceModel)
                    + "/failed_" + sanetizeString(stepName) + ".jpeg");
        }
    }

    @Override
    public void onStart(ISuite iSuite) {
        try {
            appiumServerManager.startAppiumServer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onFinish(ISuite iSuite) {
        try {
            appiumServerManager.stopAppiumServer();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


}
