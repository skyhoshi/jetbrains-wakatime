/* ==========================================================
File:        ConfigFile.java
Description: Read and write settings from the INI config file.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

public class ConfigFile {
    private static final String fileName = ".wakatime.cfg";
    private static final String internalFileName = "wakatime-internal.cfg";
    private static final String defaultDashboardUrl = "https://wakatime.com/dashboard";
    private static String cachedHomeFolder = null;
    private static String _api_key = "";
    private static String _dashboard_url = "";
    private static boolean _usingVaultCmd = false;

    private static String getConfigFilePath(boolean internal) {
        if (ConfigFile.cachedHomeFolder == null) {
            if (System.getenv("WAKATIME_HOME") != null && !System.getenv("WAKATIME_HOME").trim().isEmpty()) {
                File folder = new File(System.getenv("WAKATIME_HOME"));
                if (folder.exists()) {
                    ConfigFile.cachedHomeFolder = folder.getAbsolutePath();
                    WakaTime.log.debug("Using $WAKATIME_HOME for config folder: " + ConfigFile.cachedHomeFolder);
                    if (internal) {
                        return new File(new File(ConfigFile.cachedHomeFolder, ".wakatime"), internalFileName).getAbsolutePath();
                    }
                    return new File(ConfigFile.cachedHomeFolder, fileName).getAbsolutePath();
                }
            }
            ConfigFile.cachedHomeFolder = new File(System.getProperty("user.home")).getAbsolutePath();
            WakaTime.log.debug("Using $HOME for config folder: " + ConfigFile.cachedHomeFolder);
        }
        if (internal) {
            return new File(new File(ConfigFile.cachedHomeFolder, ".wakatime"), internalFileName).getAbsolutePath();
        }
        return new File(ConfigFile.cachedHomeFolder, fileName).getAbsolutePath();
    }

    public static String get(String section, String key, boolean internal) {
        String file = ConfigFile.getConfigFilePath(internal);
        String val = null;
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String currentSection = "";
            try {
                String line = br.readLine();
                while (line != null) {
                    if (line.trim().startsWith("[") && line.trim().endsWith("]")) {
                        currentSection = line.trim().substring(1, line.trim().length() - 1).toLowerCase();
                    } else {
                        if (section.toLowerCase().equals(currentSection)) {
                            String[] parts = line.split("=");
                            if (parts.length == 2 && parts[0].trim().equals(key)) {
                                val = parts[1].trim();
                                br.close();
                                return removeNulls(val);
                            }
                        }
                    }
                    line = br.readLine();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (FileNotFoundException e1) { /* ignored */ }
        return removeNulls(val);
    }

    public static void set(String section, String key, boolean internal, String val) {
        key = removeNulls(key);
        val = removeNulls(val);

        String file = ConfigFile.getConfigFilePath(internal);
        StringBuilder contents = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            try {
                String currentSection = "";
                String line = br.readLine();
                Boolean found = false;
                while (line != null) {
                    line = removeNulls(line);
                    if (line.trim().startsWith("[") && line.trim().endsWith("]")) {
                        if (section.toLowerCase().equals(currentSection) && !found) {
                            contents.append(key + " = " + val + "\n");
                            found = true;
                        }
                        currentSection = line.trim().substring(1, line.trim().length() - 1).toLowerCase();
                        contents.append(line + "\n");
                    } else {
                        if (section.toLowerCase().equals(currentSection)) {
                            String[] parts = line.split("=");
                            String currentKey = parts[0].trim();
                            if (currentKey.equals(key)) {
                                if (!found) {
                                    contents.append(key + " = " + val + "\n");
                                    found = true;
                                }
                            } else {
                                contents.append(line + "\n");
                            }
                        } else {
                            contents.append(line + "\n");
                        }
                    }
                    line = br.readLine();
                }
                if (!found) {
                    if (!section.toLowerCase().equals(currentSection)) {
                        contents.append("[" + section.toLowerCase() + "]\n");
                    }
                    contents.append(key + " = " + val + "\n");
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (FileNotFoundException e1) {

            // cannot read config file, so create it
            contents = new StringBuilder();
            contents.append("[" + section.toLowerCase() + "]\n");
            contents.append(key + " = " + val + "\n");
        }

        PrintWriter writer = null;
        try {
            writer = new PrintWriter(file, "UTF-8");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if (writer != null) {
            writer.print(contents.toString());
            writer.close();
        }
    }

    public static String getApiKey() {
        if (ConfigFile._usingVaultCmd) {
            return "";
        }
        if (!ConfigFile._api_key.equals("")) {
            return ConfigFile._api_key;
        }

        String apiKey = get("settings", "api_key", false);
        if (apiKey == null) {
            String vaultCmd = get("settings", "api_key_vault_cmd", false);
            if (vaultCmd != null && !vaultCmd.trim().equals("")) {
                ConfigFile._usingVaultCmd = true;
                return "";
            }
            apiKey = "";
        }

        ConfigFile._api_key = apiKey;
        return apiKey;
    }

    public static boolean usingVaultCmd() {
        return ConfigFile._usingVaultCmd;
    }

    public static void setApiKey(String apiKey) {
        set("settings", "api_key", false, apiKey);
        ConfigFile._api_key = apiKey;
    }

    public static String getDashboardUrl() {
        if (!ConfigFile._dashboard_url.equals("")) {
            return ConfigFile._dashboard_url;
        }

        String apiUrl = get("settings", "api_url", false);
        if (apiUrl == null) {
            ConfigFile._dashboard_url = ConfigFile.defaultDashboardUrl;
        } else {
            ConfigFile._dashboard_url = convertApiUrlToDashboardUrl(apiUrl);
        }
        return ConfigFile._dashboard_url;
    }

    private static String convertApiUrlToDashboardUrl(String apiUrl) {
        int apiIndex = apiUrl.indexOf("/api");
        if (apiIndex == -1) {
            return defaultDashboardUrl;
        }
        return apiUrl.substring(0, apiIndex);
    }

    private static String removeNulls(String s) {
        if (s == null) return null;
        return s.replace("\0", "");
    }

}
