package ro.redeul.google.go.components;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import ro.redeul.google.go.config.sdk.GoAppEngineSdkData;
import ro.redeul.google.go.config.sdk.GoAppEngineSdkType;
import ro.redeul.google.go.config.sdk.GoSdkData;
import ro.redeul.google.go.config.sdk.GoSdkType;
import ro.redeul.google.go.lang.psi.typing.GoTypes;
import ro.redeul.google.go.sdk.GoSdkUtil;

import java.util.ArrayList;
import java.util.List;

public class ProjectSdkValidator extends AbstractProjectComponent {
    private static final Logger LOG = Logger.getInstance(ProjectSdkValidator.class);

    public ProjectSdkValidator(Project project) {
        super(project);
    }


    @Override
    public void projectClosed() {
        //Cleanups fix issue pointing out on pr #354
        GoTypes.cachedTypes.clear();
        super.projectClosed();
    }

    @Override
    public void initComponent() {
        ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
        List<Sdk> sdkList = new ArrayList<Sdk>();

        sdkList.addAll(GoSdkUtil.getSdkOfType(GoSdkType.getInstance(), jdkTable));

        for (Sdk sdk : sdkList) {
            GoSdkData sdkData = (GoSdkData) sdk.getSdkAdditionalData();

            boolean needsUpgrade = sdkData == null;
            try {
                if (!needsUpgrade) {
                    sdkData.checkValid();
                }
            } catch (ConfigurationException ex) {
                needsUpgrade = true;
            }

            if (!needsUpgrade)
                continue;

            needsUpgrade = false;
            GoSdkData data = GoSdkUtil.testGoogleGoSdk(sdk.getHomePath());

            if (data == null)
                needsUpgrade = true;

            try {
                if (data != null) {
                    data.checkValid();
                }
            } catch (ConfigurationException ex) {
                needsUpgrade = true;
            }

            if (needsUpgrade) {
                Notifications.Bus.notify(
                        new Notification("GoLang SDK validator", "Corrupt Go SDK",
                                getContent("Go", sdk.getName()),
                                NotificationType.WARNING), myProject);
            }

            SdkModificator sdkModificator = sdk.getSdkModificator();
            sdkModificator.setSdkAdditionalData(data);
            sdkModificator.commitChanges();
        }

        sdkList.clear();
        sdkList.addAll(GoSdkUtil.getSdkOfType(GoAppEngineSdkType.getInstance(), jdkTable));

        for (Sdk sdk : sdkList) {
            GoAppEngineSdkData sdkData = (GoAppEngineSdkData) sdk.getSdkAdditionalData();

            if (sdkData == null || sdkData.TARGET_ARCH == null || sdkData.TARGET_OS == null) {
                Notifications.Bus.notify(
                        new Notification("GoLang SDK validator", "Corrupt Go SDK",
                                getContent("Go App Engine", sdk.getName()),
                                NotificationType.WARNING), myProject);
            }
        }

        String msg = "";

        try {
            if (GoSdkUtil.getSysGoRootPath() == null &&
                    GoSdkUtil.getSysGoRootPath().isEmpty()) {
                msg += "GOROOT environment variable is empty or could not be detected properly.\n";
                msg += "This means that some tools like go run or go fmt might not run properly.\n\n";
            }
        } catch (Exception e) {
            LOG.debug(e.getMessage());
            msg += "GOROOT environment variable is empty or could not be detected properly.\n";
            msg += "This means that some tools like go run or go fmt might not run properly.\n\n";
        }

        try {
            if (GoSdkUtil.getSysGoPathPath() == null &&
                    GoSdkUtil.getSysGoPathPath().isEmpty()) {
                msg += "GOPATH environment variable is empty or could not be detected properly.\n";
                msg += "This means that autocomplete might not work correctly.\n\n";
            }
        } catch (Exception e) {
            LOG.debug(e.getMessage());
            msg += "GOPATH environment variable is empty or could not be detected properly.\n";
            msg += "This means that autocomplete might not work correctly.\n";
        }

        final String message = msg;

        if (!message.isEmpty() && !ApplicationManager.getApplication().isUnitTestMode()) {
            StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
                public void run() {
                    String msg = message + "Please check the readme here: http://git.io/_InSxQ";
                    Messages.showErrorDialog(myProject, msg, "GOlang: Missing Environment Variables");
                }
            });
        }

        super.initComponent();
    }

    private String getContent(String type, String name) {
        return
                "<html>The attached " + type + " SDK named: <em>" + name + "</em> seems to be corrupt." +
                        "<br/>Please update it by going to the project sdk editor remove it and add it again.</html>";
    }
}
