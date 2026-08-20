// 临时诊断脚本：输出 :app 各变体 applicationId
projectsEvaluated {
    rootProject.project(":app").let { p ->
        val android = p.extensions.findByName("android") ?: return@let
        val variants = android.javaClass.getMethod("getApplicationVariants").invoke(android) as Iterable<*>
        variants.forEach { v ->
            val mmd = v!!.javaClass.getMethod("getMergedFlavor").invoke(v)
            val appId = mmd.javaClass.getMethod("getApplicationId").invoke(mmd)
            val bt = v.javaClass.getMethod("getBuildType").invoke(v)
            val btName = bt.javaClass.getMethod("getName").invoke(bt)
            val flavors = v.javaClass.getMethod("getProductFlavors").invoke(v) as List<*>
            val fNames = flavors.map { it!!.javaClass.getMethod("getName").invoke(it) }
            println("VARIANT ${v.javaClass.getMethod("getName").invoke(v)} -> appId=$appId buildType=$btName flavors=$fNames")
        }
    }
}
