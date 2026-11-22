# kotlinx.serialization: keep generated serializers for @Serializable classes (navigation routes, DTOs).
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.rumput.vision.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.rumput.vision.** {
    kotlinx.serialization.KSerializer serializer(...);
}
