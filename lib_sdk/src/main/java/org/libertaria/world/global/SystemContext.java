package org.libertaria.world.global;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by mati on 12/11/16.
 */

public interface SystemContext {

    FileOutputStream openFileOutputPrivateMode(String name) throws FileNotFoundException;

    FileOutputStream openFileOutput(String name, int mode) throws FileNotFoundException;

    FileInputStream openFileInput(String name) throws FileNotFoundException;

    File getFileStreamPath(String name);

    File getDir(String name, int mode);

    File getDirPrivateMode(String name);

    void startService(int service, String command, Object... args);

    void toast(String text);

    PackageInformation packageInformation();

    boolean isMemoryLow();

    InputStream openAssetsStream(String name) throws IOException;

    String getPackageName();

    void broadcastPlatformEvent(IntentMessage intentMessage);

    void showDialog(String id);

    void showDialog(String showBlockchainOffDialog, String dialogText);

    String[] fileList();

    void stopBlockchainService();
}
