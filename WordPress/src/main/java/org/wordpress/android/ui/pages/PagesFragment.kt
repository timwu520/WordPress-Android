package org.wordpress.android.ui.pages

import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.SearchView
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MenuItem.OnActionExpandListener
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.pages_fragment.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import org.wordpress.android.R
import org.wordpress.android.R.string
import org.wordpress.android.WordPress
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.ui.ActivityLauncher
import org.wordpress.android.ui.RequestCodes
import org.wordpress.android.ui.pages.PageItem.Page
import org.wordpress.android.ui.pages.PageListFragment.Companion.Type
import org.wordpress.android.ui.posts.BasicFragmentDialog
import org.wordpress.android.ui.posts.EditPostActivity
import org.wordpress.android.util.AniUtils
import org.wordpress.android.util.DisplayUtils
import org.wordpress.android.util.WPSwipeToRefreshHelper
import org.wordpress.android.util.helpers.SwipeToRefreshHelper
import org.wordpress.android.viewmodel.pages.PageListViewModel.PageListState
import org.wordpress.android.viewmodel.pages.PageListViewModel.PageListState.FETCHING
import org.wordpress.android.viewmodel.pages.PagesViewModel
import org.wordpress.android.widgets.RecyclerItemDecoration
import javax.inject.Inject

class PagesFragment : Fragment() {
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: PagesViewModel
    private lateinit var swipeToRefreshHelper: SwipeToRefreshHelper
    private lateinit var actionMenuItem: MenuItem

    companion object {
        fun newInstance(): PagesFragment {
            return PagesFragment()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.pages_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val site = if (savedInstanceState == null) {
            activity?.intent?.getSerializableExtra(WordPress.SITE) as SiteModel?
        } else {
            savedInstanceState.getSerializable(WordPress.SITE) as SiteModel?
        }

        val nonNullActivity = checkNotNull(activity)
        val nonNullSite = checkNotNull(site)

        (nonNullActivity.application as? WordPress)?.component()?.inject(this)

        initializeViews(nonNullActivity)
        initializeViewModels(nonNullActivity, nonNullSite)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == RequestCodes.EDIT_POST && resultCode == Activity.RESULT_OK && data != null) {
            val pageId = data.getLongExtra(EditPostActivity.EXTRA_POST_REMOTE_ID, -1)
            if (pageId != -1L) {
                onPageEditFinished(pageId)
            }
        }
    }

    private fun onPageEditFinished(pageId: Long) {
        viewModel.onPageEditFinished(pageId)
    }

    private fun initializeViews(activity: FragmentActivity) {
        pagesPager.adapter = PagesPagerAdapter(activity, childFragmentManager)
        tabLayout.setupWithViewPager(pagesPager)

        swipeToRefreshHelper = WPSwipeToRefreshHelper.buildSwipeToRefreshHelper(pullToRefresh) {
            viewModel.onPullToRefresh()
        }

        newPageButton.setOnClickListener {
            viewModel.onNewPageButtonTapped()
        }
    }

    private fun initializeSearchView() {
        searchRecyclerView.layoutManager = LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)
        searchRecyclerView.addItemDecoration(RecyclerItemDecoration(0, DisplayUtils.dpToPx(activity, 1)))

        actionMenuItem.setOnActionExpandListener(object : OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                return viewModel.onSearchExpanded()
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                return viewModel.onSearchCollapsed()
            }
        })

        val searchView = actionMenuItem.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                viewModel.onSearch(query)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                viewModel.onSearch(newText)
                return true
            }
        })

        // fix the search view margins to match the action bar
        val searchEditFrame = actionMenuItem.actionView.findViewById<LinearLayout>(R.id.search_edit_frame)
        (searchEditFrame.layoutParams as LinearLayout.LayoutParams)
                .apply { this.leftMargin = DisplayUtils.dpToPx(activity, -8) }
                .apply { this.rightMargin = DisplayUtils.dpToPx(activity, -12) }
    }

    private fun initializeViewModels(activity: FragmentActivity, site: SiteModel) {
        viewModel = ViewModelProviders.of(activity, viewModelFactory).get(PagesViewModel::class.java)

        setupObservers(activity, site)

        viewModel.start(site)
    }

    private fun setupObservers(activity: FragmentActivity, site: SiteModel) {
        viewModel.searchResult.observe(this, Observer { result ->
            result?.let { setSearchResult(result) }
        })

        viewModel.isSearchExpanded.observe(this, Observer {
            if (it == true) {
                showSearchList(actionMenuItem)
            } else {
                hideSearchList(actionMenuItem)
            }
        })

        viewModel.listState.observe(this, Observer {
            refreshProgressBars(it)
        })

        viewModel.createNewPage.observe(this, Observer {
            ActivityLauncher.addNewPageForResult(this, site)
        })

        viewModel.showSnackbarMessage.observe(this, Observer { holder ->
            val parent = activity.findViewById<View>(R.id.coordinatorLayout)
            if (holder != null && parent != null) {
                if (holder.buttonTitle.isNullOrEmpty()) {
                    Snackbar.make(parent, holder.message, Snackbar.LENGTH_LONG).show()
                } else {
                    val snackbar = Snackbar.make(parent, holder.message, Snackbar.LENGTH_LONG)
                    snackbar.setAction(holder.buttonTitle) { _ -> holder.buttonAction() }
                    snackbar.show()
                }
            }
        })

        viewModel.editPage.observe(this, Observer { page ->
            page?.let { ActivityLauncher.editPageForResult(this, page) }
        })

        viewModel.previewPage.observe(this, Observer { page ->
            page?.let { ActivityLauncher.viewPagePreview(this, page) }
        })

        viewModel.setPageParent.observe(this, Observer { page ->
        })

        viewModel.displayDeleteDialog.observe(this, Observer { page ->
            page?.let { displayDeleteDialog(page) }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_search, menu)
        actionMenuItem = checkNotNull(menu.findItem(R.id.action_search)) {
            "Menu does not contain mandatory search item"
        }

        initializeSearchView()
    }

    fun onPageDeleteConfirmed(remoteId: Long) {
        viewModel.onDeleteConfirmed(remoteId)
    }

    private fun refreshProgressBars(listState: PageListState?) {
        if (!isAdded || view == null) {
            return
        }
        // We want to show the swipe refresher for the initial fetch but not while loading more
        swipeToRefreshHelper.isRefreshing = listState == FETCHING
    }

    private fun hideSearchList(myActionMenuItem: MenuItem) {
        pagesPager.visibility = View.VISIBLE
        tabLayout.visibility = View.VISIBLE
        searchRecyclerView.visibility = View.GONE
        if (myActionMenuItem.isActionViewExpanded) {
            myActionMenuItem.collapseActionView()
        }
        launch(UI) {
            delay(300)
            AniUtils.scaleIn(newPageButton, AniUtils.Duration.MEDIUM)
        }
    }

    private fun showSearchList(myActionMenuItem: MenuItem) {
        pagesPager.visibility = View.GONE
        tabLayout.visibility = View.GONE
        searchRecyclerView.visibility = View.VISIBLE
        if (!myActionMenuItem.isActionViewExpanded) {
            myActionMenuItem.expandActionView()
        }
        AniUtils.scaleOut(newPageButton, AniUtils.Duration.MEDIUM)
    }

    private fun setSearchResult(pages: List<PageItem>) {
        val adapter: PagesAdapter
        if (searchRecyclerView.adapter == null) {
            adapter = PagesAdapter(
                    { action, page -> viewModel.onMenuAction(action, page) },
                    { page -> viewModel.onItemTapped(page) }
            )
            searchRecyclerView.adapter = adapter
        } else {
            adapter = searchRecyclerView.adapter as PagesAdapter
        }
        adapter.update(pages)
    }

    private fun displayDeleteDialog(page: Page) {
        val dialog = BasicFragmentDialog()
        dialog.initialize(page.id.toString(),
                getString(string.delete_page),
                getString(string.page_delete_dialog_message, page.title),
                getString(string.delete),
                getString(string.cancel))
        dialog.show(fragmentManager, page.id.toString())
    }
}

class PagesPagerAdapter(val context: Context, fm: FragmentManager) : FragmentPagerAdapter(fm) {
    companion object {
        const val PAGE_TABS = 4
    }

    override fun getCount(): Int = PAGE_TABS

    override fun getItem(position: Int): Fragment {
        return PageListFragment.newInstance(Type.getType(position))
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return Type.getType(position).text.let { context.getString(it) }
    }
}