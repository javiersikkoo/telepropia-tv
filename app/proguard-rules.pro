# Los DTO del resolver se leen por reflexión de nombres de campo en el JSON:
# si R8 los renombra o quita sus serializers, el parseo falla solo en release.
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class es.telepropia.tv.data.remote.**$$serializer { *; }
-keepclassmembers class es.telepropia.tv.data.remote.** {
    *** Companion;
}
-keepclasseswithmembers class es.telepropia.tv.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}
