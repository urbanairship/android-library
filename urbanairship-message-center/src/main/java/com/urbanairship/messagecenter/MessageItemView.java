/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.format.DateFormat;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.urbanairship.UAirship;
import com.urbanairship.images.ImageRequestOptions;
import com.urbanairship.util.AccessibilityUtils;
import com.urbanairship.util.ViewUtils;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

/**
 * Message Center item view.
 */
public class MessageItemView extends FrameLayout {

    private static final int[] STATE_HIGHLIGHTED = { R.attr.ua_state_highlighted };

    private View contentView;
    private TextView titleView;
    private TextView dateView;
    private ImageView iconView;
    private CheckBox checkBox;

    private final List<Integer> accessibilityActionIds = new ArrayList<>();

    private boolean isHighlighted;
    private OnClickListener selectionListener;

    public MessageItemView(@NonNull Context context) {
        this(context, null, R.attr.messageCenterStyle);
    }

    public MessageItemView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.messageCenterStyle);
    }

    public MessageItemView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, R.style.MessageCenter);
    }

    public MessageItemView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        init(context, attrs, defStyleAttr, defStyleRes);
    }

    /**
     * Initializes the view.
     *
     * @param context The Context the view is running in, through which it can
     * access the current theme, resources, etc.
     * @param attrs The attributes of the XML tag that is inflating the view.
     * @param defStyleAttr An attribute in the current theme that contains a
     * reference to a style resource that supplies default values for
     * the view. Can be 0 to not look for defaults.
     * @param defStyleRes A resource identifier of a style resource that
     * supplies default values for the view, used only if
     * defStyleAttr is 0 or cannot be found in the theme. Can be 0
     * to not look for defaults.
     */
    private void init(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        int contentLayout = R.layout.ua_item_mc_content;
        int dateTextAppearance;
        int titleTextAppearance;

        TypedArray attributes = context.getTheme().obtainStyledAttributes(attrs, R.styleable.MessageCenter, defStyleAttr, defStyleRes);

        if (attributes.getBoolean(R.styleable.MessageCenter_messageCenterItemIconEnabled, false)) {
            contentLayout = R.layout.ua_item_mc_icon_content;
        }

        dateTextAppearance = attributes.getResourceId(R.styleable.MessageCenter_messageCenterItemDateTextAppearance, 0);

        titleTextAppearance = attributes.getResourceId(R.styleable.MessageCenter_messageCenterItemTitleTextAppearance, 0);

        int background = attributes.getResourceId(R.styleable.MessageCenter_messageCenterItemBackground, 0);
        if (background != 0) {
            setBackgroundResource(background);
        }

        attributes.recycle();

        contentView = View.inflate(context, contentLayout, this);

        titleView = contentView.findViewById(R.id.title);
        ViewUtils.applyTextStyle(context, titleView, titleTextAppearance);

        dateView = contentView.findViewById(R.id.date);
        ViewUtils.applyTextStyle(context, dateView, dateTextAppearance);

        iconView = contentView.findViewById(R.id.image);
        if (iconView != null) {
            iconView.setOnClickListener(v -> {
                if (selectionListener != null) {
                    selectionListener.onClick(MessageItemView.this);
                }
            });
        }

        checkBox = contentView.findViewById(R.id.checkbox);
        if (checkBox != null) {
            checkBox.setOnClickListener(v -> {
                if (selectionListener != null) {
                    selectionListener.onClick(MessageItemView.this);
                }
            });
        }

    }

    /**
     * Updates the view's message.
     *
     * @param message The message.
     * @param placeholder Image place holder.
     */
    void updateMessage(@NonNull Message message, @DrawableRes int placeholder, boolean isSelected) {
        dateView.setText(DateFormat.getDateFormat(getContext()).format(message.getSentDate()));

        if (message.isRead()) {
            titleView.setText(message.getTitle());
        } else {
            SpannableString text = new SpannableString(message.getTitle());
            text.setSpan(new StyleSpan(Typeface.BOLD), 0, text.length(), 0);
            titleView.setText(text, TextView.BufferType.SPANNABLE);
        }

        if (checkBox != null) {
            checkBox.setChecked(isSelected);
        }

        if (iconView != null) {
            ImageRequestOptions options = ImageRequestOptions.newBuilder(message.getListIconUrl())
                                                             .setPlaceHolder(placeholder)
                                                             .build();

            UAirship.shared().getImageLoader().load(getContext(), iconView, options);
        }

        // Set summary content description on the message item.
        contentView.setContentDescription(buildContentDescription(getContext(), message, isSelected));
        updateAccessibilityActions(contentView, isSelected);
    }

    @Override
    public void setActivated(boolean activated) {
        super.setActivated(activated);
        if (checkBox != null) {
            checkBox.setChecked(activated);
        }
    }

    /**
     * Sets the highlight state item.
     *
     * @param isHighlighted {@code true} to highlight the view, otherwise {@code false}.
     */
    void setHighlighted(boolean isHighlighted) {
        if (this.isHighlighted != isHighlighted) {
            this.isHighlighted = isHighlighted;
            refreshDrawableState();
        }
    }

    /**
     * Sets the selection listener when the item's icon or checkbox is tapped.
     *
     * @param listener A click listener.
     */
    void setSelectionListener(@Nullable OnClickListener listener) {
        this.selectionListener = listener;
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        if (isHighlighted) {
            final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
            mergeDrawableStates(drawableState, STATE_HIGHLIGHTED);
            return drawableState;
        } else {
            return super.onCreateDrawableState(extraSpace);
        }
    }


    /**
     * Updates the view's accessibility actions.
     */
    private void updateAccessibilityActions(@NonNull View view, boolean isActivated) {
        // Clear any previously set actions to avoid duplicates.
        for (int actionId : accessibilityActionIds) {
            ViewCompat.removeAccessibilityAction(view, actionId);
        }

        // Add custom actions to support item selection on the item view.
        // This replaces checkbox/icon clicks when in screen reader mode.
        String actionLabel = getContext().getString(
            isActivated ? R.string.ua_mc_action_unselect : R.string.ua_mc_action_select);
        accessibilityActionIds.add(
            ViewCompat.addAccessibilityAction(view, actionLabel, (v, args) -> {
                if (selectionListener != null) {
                    selectionListener.onClick(MessageItemView.this);
                }
                return true;
            })
        );

        // Update click action to read "Tap to read message" instead of "Tap to activate".
        AccessibilityUtils.setClickActionLabel(view, R.string.ua_mc_action_click);
    }

    /**
     * Generates a content description for the given message.
     *
     * @param context Context.
     * @param message The message.
     * @param isSelected {@code true} if the message is selected, {@code false} otherwise.
     * @return a content description {@code String}.
     */
    @NonNull
    private static String buildContentDescription(
        @NonNull Context context,
        @NonNull Message message,
        boolean isSelected
    ) {
        StringBuilder sb = new StringBuilder();
        // Selected state
        if (isSelected) {
            sb.append(context.getString(R.string.ua_mc_description_state_selected));
        }
        // Read state
        if (!message.isRead()) {
            sb.append(context.getString(R.string.ua_mc_description_state_unread));
        }
        // Title and date
        sb.append(
            context.getString(R.string.ua_mc_description_title_and_date,
                message.getTitle(),
                DateFormat.getLongDateFormat(context).format(message.getSentDate())
            )
        );
        return sb.toString();
    }
}
