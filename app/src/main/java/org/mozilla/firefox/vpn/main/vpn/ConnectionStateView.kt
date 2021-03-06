package org.mozilla.firefox.vpn.main.vpn

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.CompoundButton
import androidx.cardview.widget.CardView
import androidx.core.graphics.drawable.DrawableCompat
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import kotlinx.android.synthetic.main.view_connection_state.view.*
import org.mozilla.firefox.vpn.R
import org.mozilla.firefox.vpn.main.vpn.VpnViewModel.UIModel
import org.mozilla.firefox.vpn.util.color
import org.mozilla.firefox.vpn.util.tint

class ConnectionStateView : CardView {

    var onSwitchListener: ((Boolean) -> Unit)? = null
    private var currentModel: UIModel = UIModel.Disconnected()

    private val onCheckedChangedListener =
        CompoundButton.OnCheckedChangeListener { button, isChecked ->
            // every time the onRestoreInstanceState() is called, onCheckedChangedListener will be
            // triggered, button.isPressed here help to check whether the change is initiated by the user
            if (button.isPressed) {
                onSwitchListener?.invoke(isChecked)
                button.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
        }

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        radius = context.resources.getDimensionPixelSize(R.dimen.vpn_state_card_radius).toFloat()
        inflate(context, R.layout.view_connection_state, this)
        switch_btn.setOnCheckedChangeListener(onCheckedChangedListener)
        ripple.frame = 0
        warning_icon.setImageDrawable(DrawableCompat.wrap(warning_icon.drawable).mutate())
    }

    fun applyUiModel(model: UIModel) {
        initGlobeAnimation(currentModel, model)
        initRippleAnimation(currentModel, model)
        initHapticFeedback(currentModel, model)

        title.text = model.title.resolve(context)

        description.text = when (model) {
            is UIModel.Connected -> model.description.resolve(context) +
                    " ${context.getString(R.string.vpn_state_separator)} "
            is UIModel.Switching -> model.description.resolve(context)
            is UIModel.NoSignal,
            is UIModel.Unstable -> " ${context.getString(R.string.vpn_state_separator)} " +
                    model.description.resolve(context)
            else -> model.description.resolve(context)
        }

        when (model) {
            is UIModel.WarningState -> {
                val color = context.color(model.stateColorId)
                warning_icon.apply {
                    visibility = View.VISIBLE
                    drawable.tint(color)
                }
                warning_text.apply {
                    visibility = View.VISIBLE
                    text = model.stateText.resolve(context)
                    setTextColor(color)
                }
            }
            else -> {
                warning_icon.visibility = View.GONE
                warning_text.visibility = View.GONE
            }
        }

        duration.visibility = if (model is UIModel.Connected) { View.VISIBLE } else { View.GONE }

        switch_btn.isEnabled = when (model) {
            is UIModel.Connecting,
            is UIModel.Disconnecting,
            is UIModel.Switching -> false
            else -> true
        }

        switchSilently(model.switchOn)

        applyStyle(model.style)
        currentModel = model
    }

    fun setDuration(duration: String) {
        this.duration.text = duration
    }

    private fun initGlobeAnimation(oldModel: UIModel, newModel: UIModel) {
        val fromAnyConnectedState = oldModel is UIModel.Connected ||
                oldModel is UIModel.Unstable ||
                oldModel is UIModel.NoSignal

        if (oldModel is UIModel.Disconnected && newModel is UIModel.Connecting) {
            globe.playOnce(0, 14)
        } else if (oldModel is UIModel.Connecting && newModel is UIModel.Connected) {
            globe.playOnce(15, 29)
        } else if (oldModel is UIModel.Connected && newModel is UIModel.Switching) {
            globe.playOnce(30, 44)
        } else if (oldModel is UIModel.Switching && newModel is UIModel.Connected) {
            globe.playOnce(45, 59)
        } else if (fromAnyConnectedState && newModel is UIModel.Disconnecting) {
            globe.playOnce(60, 74)
        } else if (oldModel is UIModel.Disconnecting && newModel is UIModel.Disconnected) {
            globe.playOnce(75, 89)
        } else if (fromAnyConnectedState && newModel is UIModel.Switching) {
            globe.playOnce(30, 44)
        } else {
            val frame = when (newModel) {
                is UIModel.Disconnected -> 0
                is UIModel.Connecting -> 15
                is UIModel.Unstable,
                is UIModel.NoSignal,
                is UIModel.WarningState,
                is UIModel.Connected -> 30
                is UIModel.Switching -> 30
                is UIModel.Disconnecting -> 75
            }
            globe.fixAtFrame(frame)
        }
    }

    private fun initRippleAnimation(oldModel: UIModel, newModel: UIModel) {
        when (newModel) {
            is UIModel.Connecting,
            is UIModel.Disconnected,
            is UIModel.NoSignal -> {
                ripple.fixAtFrame(0)
            }
        }

        val fromInsecure = !oldModel.isSecure()

        val unstableToSwitching = oldModel.isBadSignal() && newModel is UIModel.Switching
        val insecureToConnected = fromInsecure && newModel is UIModel.Connected

        val playEnterAnimation = insecureToConnected || unstableToSwitching
        val playEndAnimation = oldModel is UIModel.Connected &&
                (newModel is UIModel.Disconnecting || newModel is UIModel.Unstable)

        if (playEnterAnimation) {
            ripple.playOnce(0, 74).then { loop(75, 120) }
        } else if (playEndAnimation) {
            ripple.playOnce(ripple.frame, 210)
        }
    }

    private fun UIModel.isBadSignal() = this is UIModel.NoSignal || this is UIModel.Unstable

    private fun UIModel.isSecure() = this.isBadSignal() ||
            this is UIModel.Connected ||
            this is UIModel.Switching

    private fun initHapticFeedback(oldModel: UIModel, newModel: UIModel) {
        when {
            oldModel is UIModel.Connecting && newModel is UIModel.Connected ||
            oldModel is UIModel.Disconnecting && newModel is UIModel.Disconnected -> {
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
        }
    }

    private fun applyStyle(style: UIModel.Styles) {
        container.setBackgroundColor(context.color(style.bkgColorId))
        title.setTextColor(context.color(style.titleColorId))
        description.setTextColor(context.color(style.descriptionColorId))
        duration.setTextColor(context.color(style.descriptionColorId))

        switch_btn.alpha = style.switchAlpha

        elevation = context.resources.getDimensionPixelSize(style.bkgElevation).toFloat()
    }

    private fun switchSilently(isChecked: Boolean) {
        switch_btn.setOnCheckedChangeListener(null)
        switch_btn.isChecked = isChecked
        switch_btn.setOnCheckedChangeListener(onCheckedChangedListener)
    }
}

fun LottieAnimationView.loop(min: Int, max: Int, mode: Int = LottieDrawable.RESTART): LottieAnimationView {
    repeatCount = LottieDrawable.INFINITE
    repeatMode = mode
    setMinAndMaxFrame(min, max)
    playAnimation()
    return this
}

fun LottieAnimationView.playOnce(min: Int, max: Int): LottieAnimationView {
    repeatCount = 0
    setMinAndMaxFrame(min, max)
    playAnimation()
    return this
}

fun LottieAnimationView.fixAtFrame(frame: Int) {
    pauseAnimation()
    setMinAndMaxFrame(frame, frame)
    progress = 0f
}

fun LottieAnimationView.then(block: LottieAnimationView.() -> Unit) {
    addAnimatorListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator?) {
            removeAnimatorListener(this)
            block(this@then)
        }
    })
}
