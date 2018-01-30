package com.appium.android;

import com.appium.ios.IOSDeviceConfiguration;
import com.appium.manager.AppiumDeviceManager;
import com.appium.manager.ConfigFileManager;
import com.appium.manager.DeviceAllocationManager;
import com.appium.utils.CommandPrompt;
import com.github.yunusmete.stf.api.STFService;
import com.github.yunusmete.stf.api.ServiceGenerator;
import com.sun.javafx.binding.StringFormatter;
import com.thoughtworks.device.Device;
import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AndroidDeviceConfiguration {

    private CommandPrompt cmd = new CommandPrompt();
    public static List<String> validDeviceIds = new ArrayList<>();

    /*
     * This method gets the device model name
     */
    public String getDeviceModel() {
        String deviceModel = null;
        Optional<Device> device = getDevice();
        deviceModel = (device.get().getBrand() + " " + device.get().getDeviceModel())
                .replaceAll("[^a-zA-Z0-9\\.\\-]", "");
        return deviceModel;
    }

    /*
     * This method gets the device OS API Level
     */
    public String getDeviceOS() {
        String deviceOS = null;
        Optional<Device> device = getDevice();
        device.get().getOs();
        return deviceOS;
    }

    public String getDeviceOSVersion() {
        Optional<Device> device = getDevice();
        return device.get().getOsVersion();
    }

    private Optional<Device> getDevice() {
        Optional<Device> device = Optional.empty();
        try {
            device = DeviceAllocationManager.getInstance().deviceManager.stream().filter(deviceMan ->
                    deviceMan.getUdid().equals(AppiumDeviceManager.getDeviceUDID())).findFirst();
            if (!device.isPresent()) {
                if (DeviceAllocationManager.STF_SERVICE_URL != null
                        && DeviceAllocationManager.ACCESS_TOKEN != null) {
                    STFService stfService = ServiceGenerator.createService(STFService.class,
                            DeviceAllocationManager.STF_SERVICE_URL + "/api/v1",
                            DeviceAllocationManager.ACCESS_TOKEN);
                    Optional<com.github.yunusmete.stf.model.Device> stfDevice = stfService.getDevices().getDevices().stream().filter(androidDevice ->
                            AppiumDeviceManager.getDeviceUDID().equalsIgnoreCase(androidDevice.getSerial()) ||
                                    AppiumDeviceManager.getDeviceUDID().equalsIgnoreCase(androidDevice.getRemoteConnectUrl().toString())
                    ).findFirst();
                    if (stfDevice.isPresent()) {
                        device = stfDeviceToAdbDevice(stfDevice);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return device;
    }

    public Optional<Device> stfDeviceToAdbDevice(Optional<com.github.yunusmete.stf.model.Device> stfDevice) throws IOException, InterruptedException {
        JSONObject json = new JSONObject();
        if (ConfigFileManager.getInstance().getProperty("STF_ADB_REMOTE_CONNECT")
                .equalsIgnoreCase("true")) {
            json.put("udid", stfDevice.get().getRemoteConnectUrl().toString());
        } else {
            json.put("udid", stfDevice.get().getSerial());
        }
        if (stfDevice.get().getNotes() != null) {
            json.put("name", stfDevice.get().getNotes());
        } else {
            json.put("name", stfDevice.get().getModel());
        }
        json.put("osVersion", stfDevice.get().getVersion());
        json.put("brand", stfDevice.get().getManufacturer());
        json.put("apiLevel", stfDevice.get().getSdk());
        json.put("isDevice", "true");
        json.put("locale", getDevicLocale());
        json.put("deviceModel", stfDevice.get().getModel());
        String screenSize = StringFormatter.format("%sX%s", stfDevice.get().getDisplay().getWidth(),
                stfDevice.get().getDisplay().getHeight()).getValue();
        json.put("screenSize", screenSize);
        return Optional.of(new Device(json));
    }

    public String screenRecord(String fileName)
            throws IOException, InterruptedException {
        return "adb -s " + AppiumDeviceManager.getDeviceUDID()
                + " shell screenrecord --bit-rate 3000000 /sdcard/" + fileName
                + ".mp4";
    }

    public boolean checkIfRecordable() throws IOException, InterruptedException {
        String screenrecord =
                cmd.runCommand("adb -s " + AppiumDeviceManager.getDeviceUDID()
                        + " shell ls /system/bin/screenrecord");
        if (screenrecord.trim().equals("/system/bin/screenrecord")) {
            return true;
        } else {
            return false;
        }
    }

    public String getDeviceManufacturer()
            throws IOException, InterruptedException {
        return cmd.runCommand("adb -s " + AppiumDeviceManager.getDeviceUDID()
                + " shell getprop ro.product.manufacturer")
                .trim();
    }

    public String getDevicLocale() throws IOException, InterruptedException {
        return getDevicLocale(AppiumDeviceManager.getDeviceUDID());
    }

    public String getDevicLocale(String deviceUdid)
            throws IOException, InterruptedException {
        return cmd.runCommand("adb -s " + deviceUdid
                + " shell getprop persist.sys.locale")
                .trim();
    }

    public AndroidDeviceConfiguration pullVideoFromDevice(String fileName, String destination)
            throws IOException, InterruptedException {
        ProcessBuilder pb =
                new ProcessBuilder("adb", "-s", AppiumDeviceManager.getDeviceUDID(),
                        "pull", "/sdcard/" + fileName + ".mp4",
                        destination);
        Process pc = pb.start();
        pc.waitFor();
        System.out.println("Exited with Code::" + pc.exitValue());
        System.out.println("Done");
        Thread.sleep(5000);
        return new AndroidDeviceConfiguration();
    }

    public void removeVideoFileFromDevice(String fileName)
            throws IOException, InterruptedException {
        cmd.runCommand("adb -s " + AppiumDeviceManager.getDeviceUDID() + " shell rm -f /sdcard/"
                + fileName + ".mp4");
    }

    public void setValidDevices(List<String> deviceID) {
        deviceID.forEach(deviceList -> {
            if (deviceList.length() < IOSDeviceConfiguration.IOS_UDID_LENGTH) {
                validDeviceIds.add(deviceList);
            }
        });
    }
}
