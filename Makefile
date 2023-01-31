# Apk build script, refined by J.R. Bhaddacak
# adapted from https://gitlab.com/Matrixcoffee/hello-world-debian-android
# from the instruction at https://www.hanshq.net/command-line-android.html

SDKPATH=/usr/lib/android-sdk/build-tools/debian
PLATFORM=/usr/lib/android-sdk/platforms/android-23/android.jar
MINSDK=19
SRCPATH=src
APPPATH=$(SRCPATH)/paliplatform/tools/ppmt
VERSION=1.1.2
OUTPUT=ppmt-$(VERSION)
OBJPATH=obj

JAVAS=$(wildcard $(APPPATH)/*.java)
CLASSES=$(subst $(SRCPATH),$(OBJPATH),$(patsubst %.java,%.class,$(JAVAS)))

$(OUTPUT).apk: $(OUTPUT).aligned.apk keystore.jks
	apksigner sign --ks keystore.jks --min-sdk-version=$(MINSDK) --ks-key-alias androidkey --ks-pass pass:android --key-pass pass:android --out $@ $<

keystore.jks:
	keytool -genkeypair -keystore $@ -alias androidkey -validity 10000 -keyalg RSA -keysize 2048 -storepass android -keypass android

$(OUTPUT).aligned.apk: $(OUTPUT).unsigned.apk
	zipalign -f -p 4 $< $@

$(OUTPUT).unsigned.apk: classes.dex res.apk
	cp res.apk resx.apk
	aapt add resx.apk classes.dex
	mv resx.apk $@

classes.dex: $(CLASSES)
	$(SDKPATH)/dx --dex --min-sdk-version=$(MINSDK) --output=$@ $(OBJPATH)

$(CLASSES): $(JAVAS) $(APPPATH)/R.java
	[ -e $(OBJPATH) ] || mkdir $(OBJPATH)
	javac -Xlint:-options -bootclasspath "$(PLATFORM)" -classpath "$(SRCPATH):$(OBJPATH)" -d "$(OBJPATH)" -source 1.7 -target 1.7 $^

$(APPPATH)/R.java res.apk: AndroidManifest.xml res/*
	aapt package -f -m -I "$(PLATFORM)" -J $(SRCPATH) -S res -M AndroidManifest.xml -F res.apk

.PHONY: compile clean
compile: $(APPPATH)/R.java $(CLASSES)

clean:
	rm -vf	$(APPPATH)/R.java classes.dex *.apk *.idsig
	rm -rvf $(OBJPATH)

