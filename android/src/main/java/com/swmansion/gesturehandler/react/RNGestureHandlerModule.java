package com.swmansion.gesturehandler.react;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.facebook.react.bridge.JSApplicationIllegalArgumentException;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.PixelUtil;
import com.facebook.react.uimanager.UIManagerModule;
import com.facebook.react.uimanager.events.EventDispatcher;
import com.swmansion.gesturehandler.GestureHandler;
import com.swmansion.gesturehandler.LongPressGestureHandler;
import com.swmansion.gesturehandler.NativeViewGestureHandler;
import com.swmansion.gesturehandler.OnTouchEventListener;
import com.swmansion.gesturehandler.PanGestureHandler;
import com.swmansion.gesturehandler.TapGestureHandler;

import java.util.Map;

import javax.annotation.Nullable;

public class RNGestureHandlerModule extends ReactContextBaseJavaModule {

  public static final String MODULE_NAME = "RNGestureHandlerModule";

  private static final String KEY_SHOULD_CANCEL_WHEN_OUTSIDE = "shouldCancelWhenOutside";
  private static final String KEY_SHOULD_CANCEL_OTHERS_WHEN_ACTIVATED =
          "shouldCancelOthersWhenActivated";
  private static final String KEY_CAN_START_HANDLING_WITH_DOWN_EVENT_ONLY =
          "canStartHandlingWithDownEventOnly";
  private static final String KEY_LONG_PRESS_MIN_DURATION_MS = "minDurationMs";
  private static final String KEY_PAN_MIN_DELTA_X = "minDeltaX";
  private static final String KEY_PAN_MIN_DELTA_Y = "minDeltaY";
  private static final String KEY_PAN_MIN_DIST = "minDist";
  private static final String KEY_PAN_MAX_VELOCITY = "maxVelocity";

  private abstract static class HandlerFactory<T extends GestureHandler> {

    public abstract String getName();

    public abstract T create();

    public void configure(T handler, ReadableMap config) {
      if (config.hasKey(KEY_SHOULD_CANCEL_WHEN_OUTSIDE)) {
        handler.setShouldCancelWhenOutside(config.getBoolean(KEY_SHOULD_CANCEL_WHEN_OUTSIDE));
      }
      if (config.hasKey(KEY_SHOULD_CANCEL_OTHERS_WHEN_ACTIVATED)) {
        handler.setShouldCancelOthersWhenActivated(
                config.getBoolean(KEY_SHOULD_CANCEL_OTHERS_WHEN_ACTIVATED));
      }
      if (config.hasKey(KEY_CAN_START_HANDLING_WITH_DOWN_EVENT_ONLY)) {
        handler.setCanStartHandlingWithDownEventOnly(
                config.getBoolean(KEY_CAN_START_HANDLING_WITH_DOWN_EVENT_ONLY));
      }
    }
  }

  private static class NativeViewGestureHandlerFactory extends
          HandlerFactory<NativeViewGestureHandler> {
    @Override
    public String getName() {
      return "NativeViewGestureHandler";
    }

    @Override
    public NativeViewGestureHandler create() {
      return new NativeViewGestureHandler();
    }
  }

  private static class TapGestureHandlerFactory extends HandlerFactory<TapGestureHandler> {
    @Override
    public String getName() {
      return "TapGestureHandler";
    }

    @Override
    public TapGestureHandler create() {
      return new TapGestureHandler();
    }
  }

  private static class LongPressGestureHandlerFactory extends
          HandlerFactory<LongPressGestureHandler> {
    @Override
    public String getName() {
      return "LongPressGestureHandler";
    }

    @Override
    public LongPressGestureHandler create() {
      return new LongPressGestureHandler();
    }

    @Override
    public void configure(LongPressGestureHandler handler, ReadableMap config) {
      super.configure(handler, config);
      if (config.hasKey(KEY_LONG_PRESS_MIN_DURATION_MS)) {
        handler.setMinDurationMs(config.getInt(KEY_LONG_PRESS_MIN_DURATION_MS));
      }
    }
  }

  private static class PanGestureHandlerFactory extends HandlerFactory<PanGestureHandler> {
    @Override
    public String getName() {
      return "PanGestureHandler";
    }

    @Override
    public PanGestureHandler create() {
      return new PanGestureHandler();
    }

    @Override
    public void configure(PanGestureHandler handler, ReadableMap config) {
      super.configure(handler, config);
      if (config.hasKey(KEY_PAN_MIN_DELTA_X)) {
        handler.setMinDx(PixelUtil.toPixelFromDIP(config.getDouble(KEY_PAN_MIN_DELTA_X)));
      }
      if (config.hasKey(KEY_PAN_MIN_DELTA_Y)) {
        handler.setMinDy(PixelUtil.toPixelFromDIP(config.getDouble(KEY_PAN_MIN_DELTA_Y)));
      }
      if (config.hasKey(KEY_PAN_MIN_DIST)) {
        handler.setMinDist(PixelUtil.toPixelFromDIP(config.getDouble(KEY_PAN_MIN_DIST)));
      }
      if (config.hasKey(KEY_PAN_MAX_VELOCITY)) {
        // This value is actually in DPs/ms, but we can use the same function as for converting
        // just from DPs to pixels as the unit we're converting is in the numerator
        handler.setMaxVelocity(PixelUtil.toPixelFromDIP(config.getDouble(KEY_PAN_MAX_VELOCITY)));
      }
    }
  }

  private OnTouchEventListener mEventListener = new OnTouchEventListener() {
    @Override
    public void onTouchEvent(GestureHandler handler, MotionEvent event) {
      RNGestureHandlerModule.this.onTouchEvent(handler, event);
    }

    @Override
    public void onStateChange(GestureHandler handler, int newState, int oldState) {
      RNGestureHandlerModule.this.onStateChange(handler, newState, oldState);
    }
  };

  private HandlerFactory[] mHandlerFactories = new HandlerFactory[] {
          new NativeViewGestureHandlerFactory(),
          new TapGestureHandlerFactory(),
          new LongPressGestureHandlerFactory(),
          new PanGestureHandlerFactory()
  };
  private RNGestureHandlerRegistry mRegistry;

  public RNGestureHandlerModule(ReactApplicationContext reactContext) {
    super(reactContext);
  }

  @Override
  public String getName() {
    return MODULE_NAME;
  }

  @ReactMethod
  public void createGestureHandler(
          int viewTag,
          String handlerName,
          int handlerTag,
          ReadableMap config) {
    for (int i = 0; i < mHandlerFactories.length; i++) {
      HandlerFactory handlerFactory = mHandlerFactories[i];
      if (handlerFactory.getName().equals(handlerName)) {
        GestureHandler handler = handlerFactory.create();
        handler.setTag(handlerTag);
        handlerFactory.configure(handler, config);
        getRegistry().registerHandlerForViewWithTag(viewTag, handler);
        handler.setOnTouchEventListener(mEventListener);
        return;
      }
    }
    throw new JSApplicationIllegalArgumentException("Invalid handler name " + handlerName);
  }

  @ReactMethod
  public void dropGestureHandlersForView(int viewTag) {
    getRegistry().dropHandlersForViewWithTag(viewTag);
  }

  @Override
  public @Nullable Map getConstants() {
    return MapBuilder.of("State", MapBuilder.of(
            "UNDETERMINED", GestureHandler.STATE_UNDETERMINED,
            "BEGAN", GestureHandler.STATE_BEGAN,
            "ACTIVE", GestureHandler.STATE_ACTIVE,
            "CANCELLED", GestureHandler.STATE_CANCELLED,
            "FAILED", GestureHandler.STATE_FAILED
    ));
  }

  private RNGestureHandlerRegistry getRegistry() {
    if (mRegistry != null) {
      return mRegistry;
    }
    View contentView = getCurrentActivity().findViewById(android.R.id.content);
    View rootView = ((ViewGroup) contentView).getChildAt(0);
    if (!(rootView instanceof RNGestureHandlerEnabledRootView)) {
      throw new IllegalStateException("Trying to register gesture handler but the root view " +
              "is not setup properly " + rootView);
    }
    mRegistry = ((RNGestureHandlerEnabledRootView) rootView).getRegistry();
    return mRegistry;
  }

  @Override
  public void onCatalystInstanceDestroy() {
    if (mRegistry != null) {
      mRegistry.dropAllHandlers();
      mRegistry = null;
    }
    super.onCatalystInstanceDestroy();
  }

  private void onTouchEvent(GestureHandler handler, MotionEvent motionEvent) {
    float translationX = Float.NaN, translationY = Float.NaN;
    if (handler instanceof PanGestureHandler) {
      translationX = ((PanGestureHandler) handler).getTranslationX();
      translationY = ((PanGestureHandler) handler).getTranslationY();
    }
    EventDispatcher eventDispatcher = getReactApplicationContext()
            .getNativeModule(UIManagerModule.class)
            .getEventDispatcher();
    RNGestureHandlerEvent event = RNGestureHandlerEvent.obtain(
            handler.getView().getId(),
            handler.getTag(),
            handler.getState(),
            motionEvent.getX(),
            motionEvent.getY(),
            translationX,
            translationY);
    eventDispatcher.dispatchEvent(event);
  }

  private void onStateChange(GestureHandler handler, int newState, int oldState) {
    float translationX = Float.NaN, translationY = Float.NaN;
    if (handler instanceof PanGestureHandler) {
      translationX = ((PanGestureHandler) handler).getTranslationX();
      translationY = ((PanGestureHandler) handler).getTranslationY();
    }
    EventDispatcher eventDispatcher = getReactApplicationContext()
            .getNativeModule(UIManagerModule.class)
            .getEventDispatcher();
    RNGestureHandlerStateChangeEvent event = RNGestureHandlerStateChangeEvent.obtain(
            handler.getView().getId(),
            handler.getTag(),
            newState,
            oldState,
            handler.getX(),
            handler.getY(),
            translationX,
            translationY);
    eventDispatcher.dispatchEvent(event);
  }
}
