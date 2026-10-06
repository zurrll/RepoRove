# Retrofit ships its consumer rules. Preserve Kotlin serializer lookup for cached DTOs.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keep,includedescriptorclasses class app.reporove.core.model.**$$serializer { *; }
