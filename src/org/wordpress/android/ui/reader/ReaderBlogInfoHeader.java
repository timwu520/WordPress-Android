package org.wordpress.android.ui.reader;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.wordpress.android.R;
import org.wordpress.android.datasets.ReaderBlogTable;
import org.wordpress.android.models.ReaderBlogInfo;
import org.wordpress.android.ui.reader.actions.ReaderActions;
import org.wordpress.android.ui.reader.actions.ReaderBlogActions;
import org.wordpress.android.util.AniUtils;
import org.wordpress.android.util.FormatUtils;

public class ReaderBlogInfoHeader extends LinearLayout {
    public ReaderBlogInfoHeader(Context context){
        super(context);
        inflateView(context);
    }
    public ReaderBlogInfoHeader(Context context, AttributeSet attributes){
        super(context, attributes);
        inflateView(context);
    }
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public ReaderBlogInfoHeader(Context context, AttributeSet attributes, int defStyle){
        super(context, attributes, defStyle);
        inflateView(context);
    }

    private void inflateView(Context context) {
        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.reader_blog_info_header, this, true);
    }

    public void setBlogId(long blogId) {
        showBlogInfo(ReaderBlogTable.getBlogInfo(blogId));
        requestBlogInfo(blogId);
    }
    /*
     * show blog header with info from passed blog filled in
     */
    private void showBlogInfo(final ReaderBlogInfo blog) {
        final TextView txtBlogName = (TextView) findViewById(R.id.text_blog_name);
        final TextView txtDescription = (TextView) findViewById(R.id.text_blog_description);
        final TextView txtFollowCnt = (TextView) findViewById(R.id.text_follow_count);
        final TextView txtFollowBtn = (TextView) findViewById(R.id.text_follow_blog);
        final View divider = findViewById(R.id.divider);

        if (blog != null) {
            txtBlogName.setText(blog.getName());
            txtDescription.setText(blog.getDescription());
            txtDescription.setVisibility(blog.hasDescription() ? View.VISIBLE : View.GONE);
            String numFollowers = getResources().getString(R.string.reader_label_followers, FormatUtils.formatInt(blog.numSubscribers));
            txtFollowCnt.setText(numFollowers);

            boolean isFollowing = ReaderBlogTable.isFollowedBlogUrl(blog.getUrl());
            showBlogFollowStatus(txtFollowBtn, isFollowing);
            txtFollowBtn.setVisibility(View.VISIBLE);
            divider.setVisibility(View.VISIBLE);

            txtFollowBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleBlogFollowStatus(txtFollowBtn, blog);
                }
            });
        } else {
            txtBlogName.setText(null);
            txtDescription.setText(null);
            txtFollowCnt.setText(null);
            txtFollowBtn.setVisibility(View.INVISIBLE);
            divider.setVisibility(View.INVISIBLE);
        }
    }

    /*
    * request latest info for this blog
    */
    private void requestBlogInfo(final long blogId) {
        ReaderActions.RequestBlogInfoListener listener = new ReaderActions.RequestBlogInfoListener() {
            @Override
            public void onResult(ReaderBlogInfo blogInfo) {
                if (blogInfo != null) {
                    showBlogInfo(blogInfo);
                }
            }
        };
        ReaderBlogActions.updateBlogInfo(blogId, listener);
    }


    private void toggleBlogFollowStatus(final TextView txtFollow, final ReaderBlogInfo blogInfo) {
        if (blogInfo == null || txtFollow == null) {
            return;
        }

        AniUtils.zoomAction(txtFollow);

        boolean isCurrentlyFollowing = ReaderBlogTable.isFollowedBlogUrl(blogInfo.getUrl());
        ReaderBlogActions.BlogAction blogAction = (isCurrentlyFollowing ? ReaderBlogActions.BlogAction.UNFOLLOW : ReaderBlogActions.BlogAction.FOLLOW);
        if (!ReaderBlogActions.performBlogAction(blogAction, blogInfo.blogId, blogInfo.getUrl()))
            return;

        boolean isNowFollowing = !isCurrentlyFollowing;
        showBlogFollowStatus(txtFollow, isNowFollowing);
    }

    /*
     * updates the follow button in the blog header to match whether the current
     * user is following this blog
     */
    private void showBlogFollowStatus(TextView txtFollow, boolean isFollowed) {
        String following = getContext().getString(R.string.reader_btn_unfollow).toUpperCase();
        String follow = getContext().getString(R.string.reader_btn_follow).toUpperCase();

        txtFollow.setSelected(isFollowed);
        txtFollow.setText(isFollowed ? following : follow);
        int drawableId = (isFollowed ? R.drawable.note_icon_following : R.drawable.note_icon_follow);
        txtFollow.setCompoundDrawablesWithIntrinsicBounds(drawableId, 0, 0, 0);
    }
}