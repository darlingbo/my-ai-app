package com.myai.app

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Rewarded ads (Google AdMob).
 * NOTE: uses Google's TEST ad unit ID. Replace REWARDED_UNIT with your real
 * ad unit ID from admob.google.com to start earning money.
 */
object Ads {
    // Your real AdMob rewarded ad unit
    private const val REWARDED_UNIT = "ca-app-pub-5129845402568984/7617998115"
    private var initialized = false

    fun init(ctx: Context) {
        if (initialized) return
        try { MobileAds.initialize(ctx) {}; initialized = true } catch (_: Exception) {}
    }

    /** Show one rewarded ad. onReward() fires only if the user finishes watching. */
    fun showRewarded(activity: Activity, onReward: () -> Unit, onUnavailable: () -> Unit = {}) {
        try {
            RewardedAd.load(activity, REWARDED_UNIT, AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        ad.show(activity) { _ -> onReward() }
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        onUnavailable()
                    }
                })
        } catch (_: Exception) { onUnavailable() }
    }
}
