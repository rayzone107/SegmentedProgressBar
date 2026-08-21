# Consumer ProGuard/R8 rules shipped inside the AAR.
#
# aapt2 already generates keep rules for views referenced from a consumer's XML
# layouts, but a consumer that only instantiates the view programmatically (or
# inflates it from a layout in another module) gets no such rule. Keeping the
# View constructors explicitly makes the library safe in both cases.
-keepclassmembers class com.rachitgoyal.segmented.SegmentedProgressBar {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public <init>(android.content.Context, android.util.AttributeSet, int, int);
}
