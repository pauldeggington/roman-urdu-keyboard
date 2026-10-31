plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("gemma3pack")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
