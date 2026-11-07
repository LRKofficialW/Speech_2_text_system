# Keep models and serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.neuroscribe.core.storage.models.** { *; }
-keep class com.neuroscribe.core.ai.** { *; }
-keep class ai.onnxruntime.** { *; }
