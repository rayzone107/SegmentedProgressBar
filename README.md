# Segmented Progress Bar

Segmented Progress Bar allows you to create progress bars with Segments. And you can update the segments in any sequence.

This comes in very handy when you wish to inform the user about completed/pending segments of the app.

![alt text](/Image.png)

<h3>Sample App</h3>

Download the Demo app on Google Play Store:

<a href='https://play.google.com/store/apps/details?id=com.rachitgoyal.segmentedprogressbar'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png' width="160" height="65"></a>

<h2>Installation</h2>

<h3>Gradle</h3>

Add JitPack in your root build.gradle at the end of repositories:

```
allprojects {
	repositories {
        ...
		maven { url 'https://jitpack.io' }
    }
}
```

Add this line as a dependency in your app's build.gradle file
```
dependencies {
    implementation 'com.github.rayzone107:durationview:0.0.1'
}
```

<h2>How to use</h2>

<b>Add a SegmentedProgressBar to your XML layout</b>

Below are the default values for all the properties
```xml
<com.rachitgoyal.segmented.SegmentedProgressBar
        android:id="@+id/segmented_progress_bar"
        android:layout_width="match_parent"
        android:layout_height="10dp"
        
        app:divisions="30"                          \\ Integer value for total number of divisions
        app:progressBarBackgroundColor="#b4b4b4"    \\ Progress bar background color
        app:progressBarColor="#3d70d6"              \\ Progress bar color
        
        app:dividerColor="#ffffff"                  \\ Divider color
        app:dividerWidth="0dp"                      \\ Divider width - set 0dp for no divider
        app:isDividerEnabled="true"                 \\ Whether to show divider or not - no effect if dividerWidth not set
        app:cornerRadius="4dp"                      \\ Corner radius for the progress bar
 />
```

These values can also be set programatically.

```java

        SegmentedProgressBar spb = findViewById(R.id.segmented_progress_bar);
        
        spb.setDivisions(10);
        spb.setProgressBarBackgroundColor(Color.GREY);
        spb.setProgressBarColor(Color.BLUE);
        spb.setDividerColor(Color.WHITE);
        spb.setDividerWidth(10);                        \\ in px (best to set this using xml, or convert from dp to px first)
        
        spb.setEnabledDivisions(Arrays.asList(1, 3, 4, 6, 8, 9)); \\ pass a list of integers, with the values that are to be enabled.
```

Now within the code, you can anytime change the value of the enabled/disabled divisions by simply making a call to `spb.setEnabledDivisions(enabledList)`.

<h2>Contribution</h2>

Any kind of contribution will be appreciated; feel free to create a pull request or file issues on the issue tracker.
