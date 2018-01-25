package com.appium.manager;

import com.appium.android.AndroidDeviceConfiguration;
import com.appium.ios.IOSDeviceConfiguration;
import com.appium.ios.SimManager;
import com.appium.utils.AvailablePorts;
import com.appium.utils.JsonParser;
import com.github.yunusmete.stf.api.STFService;
import com.github.yunusmete.stf.api.ServiceGenerator;
import com.github.yunusmete.stf.model.DeviceBody;
import com.github.yunusmete.stf.rest.DeviceResponse;
import com.github.yunusmete.stf.rest.RemoteConnectResponse;
import com.thoughtworks.android.AndroidManager;
import com.thoughtworks.device.Device;
import com.thoughtworks.device.DeviceManager;
import com.thoughtworks.iOS.IOSManager;
import com.vdurmont.semver4j.Semver;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang3.SystemUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import static com.sun.jmx.snmp.ThreadContext.contains;

/**
 * DeviceAllocationManager - Handles device initialisation, allocation and de-allocattion
 */
public class DeviceAllocationManager {
    private ArrayList<String> devices = new ArrayList<>();
    public ConcurrentHashMap<String, Object> deviceMapping;
    private static DeviceAllocationManager instance;
    private static IOSManager iosDevice;
    private static AndroidManager androidManager;
    public List<Device> deviceManager;
    public static final String STF_SERVICE_URL = ConfigFileManager.getInstance().getProperty("STF_URL");
    public static final String ACCESS_TOKEN = ConfigFileManager.getInstance().getProperty("STF_ACCESS_TOKEN");
    public static STFService stfService;
    private static final Logger LOGGER = Logger.getLogger(Class.class.getName());
    private SimManager simManager = new SimManager();
    private AppiumDriverManager appiumDriverManager;
    private static boolean simCapsPresent = false;
    private static boolean deviceCapsPresent = false;
    private ConfigFileManager configFileManager;

    private DeviceAllocationManager() throws Exception {
        try {
            iosDevice = new IOSManager();
            deviceMapping = new ConcurrentHashMap<>();
            androidManager = new AndroidManager();
            appiumDriverManager = new AppiumDriverManager();
            configFileManager = ConfigFileManager.getInstance();
            stfService = ServiceGenerator.createService(STFService.class,
                    STF_SERVICE_URL + "/api/v1", ACCESS_TOKEN);
            deviceManager = new CopyOnWriteArrayList<>(new DeviceManager().getDeviceProperties());
            connectToSTF();
        } catch (IOException e) {
            throw new Exception(e.getMessage(), e);
        }
        setFlagsForCapsValues();
        initializeDevices();
    }

    public static DeviceAllocationManager getInstance() throws Exception {
        if (instance == null) {
            instance = new DeviceAllocationManager();
        }
        return instance;
    }

    private void initializeDevices() throws Exception {
        isPlatformInEnv();
        if (SystemUtils.IS_OS_MAC) {
            List<Device> allSimulatorDetails = new IOSDeviceConfiguration()
                    .checkIfUserSpecifiedSimulatorAndGetUDID();
            String platform = System.getenv("Platform");
            if (platform.equalsIgnoreCase("iOS")) {
                LOGGER.info("Adding only iOS Devices");

                if (IOSDeviceConfiguration.validDeviceIds.size() > 0) {
                    LOGGER.info("Adding iOS Devices from DeviceList Provided");
                    devices.addAll(IOSDeviceConfiguration.validDeviceIds);
                } else {
                    if (deviceCapsPresent) {
                        iosDevice.getAllAvailableDevices()
                                .forEach(device -> devices.add(device.getUdid()));
                    }
                    if (simManager.isSimulatorAvailable() && simCapsPresent) {
                        allocateUniqueSimulatorDetails(allSimulatorDetails);
                        allSimulatorDetails.forEach(device -> devices.add(device.getUdid()));
                    }
                }
            }
            if (platform.equalsIgnoreCase("android")) {
                if (AndroidDeviceConfiguration.validDeviceIds.size() > 0) {
                    LOGGER.info("Adding Android Devices from DeviceList Provided");
                    devices.addAll(AndroidDeviceConfiguration.validDeviceIds);
                } else {
                    androidManager.getDeviceProperties()
                            .forEach(device -> this.devices.add(device.getUdid()));
                }
            }
            if (platform.equalsIgnoreCase("Both")) {
                if (simManager.isSimulatorAvailable()) {
                    allocateUniqueSimulatorDetails(allSimulatorDetails);
                }
                getAllConnectedDevices();
            }
        } else {
            androidManager.getDeviceProperties().forEach(device -> devices.add(device.getUdid()));
        }
        for (String device : devices) {
            HashMap<Object, Object> deviceState = new HashMap<>();
            deviceState.put("deviceState", true);
            deviceState.put("port", new AvailablePorts().getPort());
            deviceMapping.put(device, deviceState);
        }
    }

    private void setFlagsForCapsValues() throws FileNotFoundException {
        String filePath = getCapsFilePath();
        JSONArray jsonParsedObject = new JsonParser(filePath).getJsonParsedObject();
        Object getPlatformObject = jsonParsedObject.stream().filter(o -> ((JSONObject) o)
                .get("iOS") != null)
                .findFirst();
        Object platFormCapabilities = ((JSONObject) ((Optional) getPlatformObject)
                .get()).get("iOS");

        ((JSONObject) platFormCapabilities).forEach((caps, values) -> {
            if ("app".equals(caps) && values instanceof JSONObject) {
                if ((((JSONObject) values).get("simulator") != null)) {
                    simCapsPresent = true;
                }
                if ((((JSONObject) values).get("device") != null)) {
                    deviceCapsPresent = true;
                }
            }
        });
    }

    private String getCapsFilePath() throws FileNotFoundException {
        String filePath = appiumDriverManager.getCapsPath();
        if (new File(filePath).exists()) {
            Path path = FileSystems.getDefault().getPath(filePath);
            if (!path.getParent().isAbsolute()) {
                filePath = path.normalize()
                        .toAbsolutePath().toString();
            }
            return filePath;
        } else {
            throw new FileNotFoundException("Capability file not found");
        }
    }

    private void allocateUniqueSimulatorDetails(List<Device> allSimulatorDetails) {
        allSimulatorDetails.stream().forEach(device -> {
            Optional<Device> first = deviceManager.stream().filter(device1 -> device.getUdid()
                    .equals(device1.getUdid())).findFirst();

            if (!first.isPresent()) {
                deviceManager.add(device);
            }

        });
    }

    private void isPlatformInEnv() {
        if (System.getenv("Platform") == null) {
            throw new IllegalArgumentException("Please execute with Platform environment"
                    + ":: Platform=android/ios/both mvn clean -Dtest=Runner test");
        }
    }

    private void getAllConnectedDevices() {
        if (IOSDeviceConfiguration.validDeviceIds.size() > 0) {
            System.out.println("Adding iOS Devices from DeviceList Provided");
            devices.addAll(IOSDeviceConfiguration.validDeviceIds);
        }
        if (AndroidDeviceConfiguration.validDeviceIds.size() > 0) {
            System.out.println("Adding Android Devices from DeviceList Provided");
            devices.addAll(AndroidDeviceConfiguration.validDeviceIds);
        } else {
            deviceManager.forEach(device -> {
                if (device.getUdid().length() == IOSDeviceConfiguration.SIM_UDID_LENGTH
                        && new SimManager().isSimulatorAvailable() && simCapsPresent) {
                    devices.add(device.getUdid());
                } else if (device.getUdid().length() == IOSDeviceConfiguration.IOS_UDID_LENGTH
                        && deviceCapsPresent) {
                    devices.add(device.getUdid());
                } else if (device.getUdid().length() < IOSDeviceConfiguration.SIM_UDID_LENGTH) {
                    devices.add(device.getUdid());
                }
            });
        }
    }

    private void connectToSTF() {
        try {
            if (!System.getenv("Platform").equalsIgnoreCase("iOS") &&
                    STF_SERVICE_URL != null && ACCESS_TOKEN != null) {
                connectToSTFServer();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ArrayList<String> getDevices() {
        LOGGER.info("All devices connected" + devices);
        return devices;
    }

    public synchronized String getNextAvailableDeviceId() {
        ConcurrentHashMap.KeySetView<String, Object> devices = deviceMapping.keySet();
        int i = 0;
        for (String device : devices) {
            Thread t = Thread.currentThread();
            t.setName("Thread_" + i);
            i++;
            if (((HashMap) deviceMapping.get(device))
                    .get("deviceState").toString().equals("true")) {
                ((HashMap) deviceMapping.get(device)).put("deviceState", false);
                return device;
            }
        }
        return null;
    }

    public void freeDevice() {
        ((HashMap) deviceMapping.get(AppiumDeviceManager.getDeviceUDID())).put("deviceState", true);
        LOGGER.info("DeAllocated Device " + AppiumDeviceManager.getDeviceUDID()
                + " from execution list");
    }

    public void allocateDevice(String device, String deviceUDID) {
        if (device.isEmpty()) {
            LOGGER.info("Allocated Device " + deviceUDID + " for Execution");
            AppiumDeviceManager.setDeviceUDID(deviceUDID);
        } else {
            LOGGER.info("Allocated Device " + deviceUDID + " for Execution");
            AppiumDeviceManager.setDeviceUDID(device);
        }
    }

    private Boolean isAllowed(String serial, String version) {
        Boolean allowed = true;
        ConfigFileManager config = ConfigFileManager.getInstance();
        if (config.getProperty("ONLY_DEVICES") != null) {
            allowed = Arrays.asList(config.getProperty("ONLY_DEVICES").split(",")).contains(serial);
        }
        if (config.getProperty("SKIP_DEVICES") != null) {
            if (Arrays.asList(config.getProperty("SKIP_DEVICES").split(",")).contains(serial)) {
                allowed = false;
            }
        }
        Semver deviceOSVersion = new Semver(version, Semver.SemverType.LOOSE);
        if (config.getProperty("MIN_OS_VERSION") != null) {
            if (deviceOSVersion.isLowerThan(config.getProperty("MIN_OS_VERSION"))) {
                allowed = false;
            }
        }
        if (config.getProperty("MAX_OS_VERSION") != null) {
            if (deviceOSVersion.isGreaterThan(config.getProperty("MAX_OS_VERSION"))) {
                allowed = false;
            }
        }
        return allowed;
    }

    private void connectToSTFServer() {
        DeviceResponse devices = stfService.getDevices();
        for (com.github.yunusmete.stf.model.Device device : devices.getDevices()) {
            if (device.isPresent() && device.isReady()) {
                if (device.getOwner() == null) {
                    if (isAllowed(device.getSerial(), device.getVersion())) {
                        stfService.addDeviceToUser(new DeviceBody(device.getSerial(), 90000));
                        if (ConfigFileManager.getInstance().getProperty("STF_ADB_REMOTE_CONNECT").equalsIgnoreCase("true")) {
                            RemoteConnectResponse remoteConnectResponse = stfService.remoteConnectDeviceBySerial(device.getSerial(), new DeviceBody(device.getSerial(), 90000));
                            if (remoteConnectResponse.isSuccess()) {
                                device.setRemoteConnect(true);
                                device.setRemoteConnectUrl(remoteConnectResponse.getRemoteConnectUrl());
                                try {
                                    androidManager.connectRemote(remoteConnectResponse.getRemoteConnectUrl());
                                    deviceManager.add(new AndroidDeviceConfiguration()
                                            .stfDeviceToAdbDevice(Optional.of(device)).get());
                                    Thread.sleep(100);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}