package com.urbanairship.chat.ui

import android.graphics.Canvas
import android.graphics.Rect
import android.os.Bundle
import android.text.format.DateUtils
import android.text.format.DateUtils.FORMAT_ABBREV_ALL
import android.text.format.DateUtils.FORMAT_SHOW_DATE
import android.text.format.DateUtils.FORMAT_SHOW_YEAR
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.UAirship
import com.urbanairship.chat.ChatDirection
import com.urbanairship.chat.R
import com.urbanairship.chat.databinding.UaFragmentChatBinding
import com.urbanairship.images.ImageLoader.ImageLoadedCallback
import com.urbanairship.images.ImageRequestOptions
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

class ChatFragment : Fragment() {

    companion object {
        /**
         * Optional `String` argument specifying a message to pre-fill the message input box on launch.
         */
        const val ARG_DRAFT = "message_draft"
    }

    private val viewModel: ChatViewModel by lazy {
        ViewModelProvider(this, ChatViewModel.ChatViewModelFactory(
                application = requireActivity().application,
                messageDraft = arguments?.getString(ARG_DRAFT)
        )).get(ChatViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val themedContext = ContextThemeWrapper(requireContext(), R.style.UrbanAirship_Chat)
        val themedInflater = inflater.cloneInContext(themedContext)
        val binding = UaFragmentChatBinding.inflate(themedInflater, container, false)

        val messageAdapter = MessageAdapter()

        viewModel.messages.observe(viewLifecycleOwner, {
            messageAdapter.submitList(it) {
                binding.messagesList.post {
                    binding.messagesList.invalidateItemDecorations()
                }
            }
        })

        binding.lifecycleOwner = this@ChatFragment
        binding.viewModel = this@ChatFragment.viewModel

        with(binding.chatMessageInput) {
            setListener(object : ChatInputEditText.ChatInputListener {
                override fun onImageSelected(imageUri: String) {
                    viewModel.setImage(imageUri)
                    binding.attachmentThumbnail.loadAttachment(imageUri) {
                        viewModel.onAttachmentThumbnailLoaded()
                    }
                }

                override fun onActionDone() = viewModel.send()
            })
        }

        with(binding.messagesList) {
            val linearLayoutManager = LinearLayoutManager(requireContext()).apply {
                // Stack from end to show new message below older ones, with gravity pulling
                // all messages to the bottom of the list view.
                stackFromEnd = true
                // Don't reverse the layout so that new messages are inserted on the bottom.
                reverseLayout = false
            }

            val res = requireContext().resources
            addItemDecoration(BottomPaddingDecoration(res.getDimension(
                    R.dimen.ua_chat_item_decoration_bottom_padding).roundToInt()))
            addItemDecoration(DateHeaderDecoration(res.getDimension(
                    R.dimen.ua_chat_item_decoration_header_height).roundToInt()))

            adapter = messageAdapter
            layoutManager = linearLayoutManager

            // Scroll to end when a message is sent.
            messageAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    val last = messageAdapter.itemCount - 1
                    val lastDirection = messageAdapter.getChatMessage(last)?.direction
                    if (positionStart == last) {
                        val lastVisibleItem = linearLayoutManager.findLastCompletelyVisibleItemPosition()
                        if (lastDirection == ChatDirection.OUTGOING || lastVisibleItem == positionStart - 1) {
                            scrollToPosition(last)
                        }
                    }
                }
            })
        }

        return binding.root
    }
}

internal fun ImageView.loadAttachment(url: String?, callback: ImageLoadedCallback? = null) {
    if (url == null) return

    val options = ImageRequestOptions.newBuilder(url).apply {
            if (callback != null) {
                setImageLoadedCallback(callback)
            }
        }
        .build()

    UAirship.shared().imageLoader.load(context, this, options)
}

internal class BottomPaddingDecoration(private val bottomPadding: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = (view.layoutParams as RecyclerView.LayoutParams).viewLayoutPosition
        if (position == parent.adapter?.itemCount?.minus(1) ?: 0) {
            outRect.set(0, 0, 0, bottomPadding)
        }
    }
}

internal class DateHeaderDecoration(private val headerHeight: Int) : RecyclerView.ItemDecoration() {
    private var headerView: View? = null

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)

        val adapter = (parent.adapter as? MessageAdapter) ?: return
        for (i: Int in 0..parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            val current = adapter.getChatMessage(position)?.createdOn
            val previous = adapter.getChatMessage(position - 1)?.createdOn

            if (current != null && !isSameDay(current, previous)) {
                drawHeader(c, child, current, headerView ?: inflateHeader(parent))
            }
        }
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state)

        val adapter = parent.adapter as? MessageAdapter ?: return

        val position = parent.getChildAdapterPosition(view)
        val current = adapter.getChatMessage(position)?.createdOn
        val previous = adapter.getChatMessage(position - 1)?.createdOn

        if (!isSameDay(current, previous)) {
            outRect.top = headerHeight
        }
    }

    private fun drawHeader(canvas: Canvas, child: View, timestamp: Long, header: View) {
        header.findViewById<TextView>(R.id.date_header).apply {
            text = DateUtils.formatDateTime(child.context, timestamp,
                    FORMAT_ABBREV_ALL or FORMAT_SHOW_DATE or FORMAT_SHOW_YEAR)
        }

        with(canvas) {
            save()
            translate(0f, (child.top - header.height).toFloat())
            header.draw(this)
            restore()
        }
    }

    private fun isSameDay(currentTimestamp: Long?, nextTimestamp: Long?): Boolean {
        if (currentTimestamp == null || nextTimestamp == null) return false

        val zone = TimeZone.getTimeZone("UTC")
        val cal1 = Calendar.getInstance(zone)
        cal1.timeInMillis = currentTimestamp
        val cal2 = Calendar.getInstance(zone)
        cal2.timeInMillis = nextTimestamp

        return cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) &&
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun inflateHeader(parent: RecyclerView): View {
        val view = LayoutInflater.from(parent.context).inflate(
                R.layout.ua_item_chat_date_header, parent, false)

        val widthSpec = MeasureSpec.makeMeasureSpec(parent.width, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(headerHeight, MeasureSpec.EXACTLY)
        val childWidth = ViewGroup.getChildMeasureSpec(
                widthSpec, parent.paddingLeft + parent.paddingRight, view.layoutParams.width)
        val childHeight = ViewGroup.getChildMeasureSpec(
                heightSpec, parent.paddingTop + parent.paddingBottom, view.layoutParams.height)

        view.measure(childWidth, childHeight)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        headerView = view
        return view
    }
}
