LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_PRIVATE_PLATFORM_APIS := false
LOCAL_PACKAGE_NAME := AgustindevStats
LOCAL_CERTIFICATE := platform
LOCAL_STATIC_JAVA_LIBRARIES := androidx.core_core androidx.appcompat_appcompat androidx.preference_preference

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)
LOCAL_MODULE := StudioLibs
LOCAL_MODULE_CLASS := FAKE
LOCAL_MODULE_SUFFIX := -timestamp
agustindevstats_system_deps := $(call java-lib-deps,framework)
agustindevstats_system_libs_path := $(abspath $(LOCAL_PATH))/system_libs

include $(BUILD_SYSTEM)/base_rules.mk

.PHONY: copy_agustindevstats_system_deps
copy_agustindevstats_system_deps: $(agustindevstats_system_deps)
	$(hide) mkdir -p $(agustindevstats_system_libs_path)
	$(hide) rm -rf $(agustindevstats_system_libs_path)/*.jar
	$(hide) cp $(agustindevstats_system_deps) $(agustindevstats_system_libs_path)/framework.jar

$(LOCAL_BUILT_MODULE): copy_agustindevstats_system_deps
	$(hide) echo "Fake: $@"
	$(hide) mkdir -p $(dir $@)
	$(hide) touch $@
