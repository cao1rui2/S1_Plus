package my.s1.app;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import com.loopj.android.http.TextHttpResponseHandler;
import my.s1.app.adapter.ManiListAdapter;
import my.s1.app.adapter.SubListAdapter;
import my.s1.app.models.MainForumItem;
import my.s1.app.models.SubForumItem;
import my.s1.app.util.MyHttpClient;
import my.s1.app.util.ParseHtml;
import org.apache.http.Header;

import java.util.ArrayList;
import java.util.HashMap;


public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener,
        AdapterView.OnItemLongClickListener, SwipeRefreshLayout.OnRefreshListener,
        AbsListView.OnScrollListener, AdapterView.OnItemSelectedListener {

    private final int LEVEL_MainForum = 0;
    private final int LEVEL_SubForum = 1;
    private final int LEVEL_FAVORITE = 2;
    private final String favoriteListUrl = "http://bbs.saraba1st.com/2b/home.php?mod=space&do=favorite&view=me";

    private int currentLevel;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ListView listView;
    private ManiListAdapter maniListAdapter;
    private SubListAdapter subListAdapter;
    private ArrayAdapter<MainForumItem> spinnerAdapter;

    private ArrayList<MainForumItem> mainForumItems = new ArrayList<MainForumItem>();
    private ArrayList<SubForumItem> subForumItems = new ArrayList<SubForumItem>();
    private ArrayList<MainForumItem> subChildren = new ArrayList<MainForumItem>();
    private HashMap<String, ArrayList<SubForumItem>> subForumMap = new HashMap<String, ArrayList<SubForumItem>>();
    private HashMap<String, ArrayList<MainForumItem>> subChildrenMap = new HashMap<String, ArrayList<MainForumItem>>();
    private MainForumItem subForum;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        listView = (ListView) findViewById(R.id.listview);
        maniListAdapter = new ManiListAdapter(this, R.layout.forum_item, mainForumItems);
        subListAdapter = new SubListAdapter(this, R.layout.forum_item, subForumItems);
        listView.setOnItemClickListener(this);
        listView.setOnItemLongClickListener(this);
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_layout);
        swipeRefreshLayout.setOnRefreshListener(this);
        swipeRefreshLayout.setColorSchemeResources(android.R.color.holo_blue_dark);
        listView.setOnScrollListener(this);
        loadMainList();
    }


    private void loadMainList() {
        String url = "http://bbs.saraba1st.com/2b/forum.php";
        listView.setAdapter(maniListAdapter);
        currentLevel = LEVEL_MainForum;
        if (mainForumItems.isEmpty()) {
            loadForumListFromNet(url);
        }
    }

    private void loadSubList() {
        String url = subForum.url;
        listView.setAdapter(subListAdapter);
        currentLevel = LEVEL_SubForum;
        subForumItems.clear();
        ArrayList<SubForumItem> forumItems = subForumMap.get(url);
        if (forumItems == null || forumItems.isEmpty()) {
            loadForumListFromNet(url);
        } else {
            if (subChildren.isEmpty()) {
                subChildren.addAll(subChildrenMap.get(subForum.url));
                spinnerAdapter.notifyDataSetChanged();
            }
            subForumItems.addAll(forumItems);
            subListAdapter.notifyDataSetChanged();
        }
    }

    private void loadFavorite() {
        listView.setAdapter(subListAdapter);
        currentLevel = LEVEL_FAVORITE;
        subForumItems.clear();
        ArrayList<SubForumItem> favorites = subForumMap.get(favoriteListUrl);
        if (favorites == null || favorites.isEmpty()) {
            loadForumListFromNet(favoriteListUrl);
        } else {
            subForumItems.addAll(favorites);
            subListAdapter.notifyDataSetChanged();
        }
    }

    private void loadForumListFromNet(final String url) {
        swipeRefreshLayout.setRefreshing(true);

        MyHttpClient.get(url, new TextHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                MyHttpClient.onFailure(statusCode, headers, responseString, throwable);
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseString) {
                if (currentLevel == LEVEL_MainForum) {
                    mainForumItems.addAll(ParseHtml.parseMainForum(responseString));
                    maniListAdapter.notifyDataSetChanged();
                } else if (currentLevel == LEVEL_SubForum) {
                    if (subChildren.isEmpty()) {
                        ArrayList<MainForumItem> subChildrenItems = ParseHtml.parseSubChildren(subForum, responseString);
                        subChildrenMap.put(subForum.url, subChildrenItems);
                        subChildren.addAll(subChildrenItems);
                        spinnerAdapter.notifyDataSetChanged();
                    }
                    ArrayList<SubForumItem> forumItems = ParseHtml.parseSubForum(responseString);
                    subForumItems.addAll(forumItems);
                    forumItems.clear();
                    forumItems.addAll(subForumItems);
                    subForumMap.put(subForum.url, forumItems);
                    subListAdapter.notifyDataSetChanged();
                } else if (currentLevel == LEVEL_FAVORITE) {
                    ArrayList<SubForumItem> forumItems = ParseHtml.parseFavorite(responseString);
                    subForumItems.addAll(forumItems);
                    subForumMap.put(favoriteListUrl, forumItems);
                    subListAdapter.notifyDataSetChanged();
                }
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuItem item = menu.findItem(R.id.spinner);
        Spinner spinner = (Spinner) MenuItemCompat.getActionView(item);
        spinnerAdapter = new ArrayAdapter<MainForumItem>(this, R.layout.spinner_layout, subChildren);
        spinner.setAdapter(spinnerAdapter);
        spinner.setOnItemSelectedListener(this);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_login) {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
            return true;
        } else if (id == R.id.show_favorite && !swipeRefreshLayout.isRefreshing()) {
            loadFavorite();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        if (currentLevel == LEVEL_MainForum) {
            subForum = mainForumItems.get(position);
            loadSubList();
        } else if (currentLevel == LEVEL_SubForum || currentLevel == LEVEL_FAVORITE) {
            Intent intent = new Intent(this, TopicActivity.class);
            SubForumItem item = subForumItems.get(position);
            if (item.url.equals("favorite")) {
                loadMainList();
            } else {
                intent.putExtra("url", item.url);
                startActivity(intent);
            }
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
        if (currentLevel == LEVEL_MainForum) {
            subForum = mainForumItems.get(position);
            currentLevel = LEVEL_SubForum;
            onRefresh();
        } else if (currentLevel == LEVEL_SubForum) {
            Intent intent = new Intent(this, TopicActivity.class);
            SubForumItem item = subForumItems.get(position);
            int pageCount = Integer.valueOf(item.num) / 30 + 1;
            String url = item.url.replaceFirst("\\d+(-\\d+\\.html)", String.valueOf(pageCount) + "$1");
            intent.putExtra("url", url);
            startActivity(intent);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (!swipeRefreshLayout.isRefreshing() && (currentLevel == LEVEL_SubForum || currentLevel == LEVEL_FAVORITE)) {
            loadMainList();
            subChildren.clear();
            spinnerAdapter.notifyDataSetChanged();
        } else if (currentLevel == LEVEL_MainForum) {
            finish();
        }
    }

    @Override
    public void onRefresh() {
        if (currentLevel == LEVEL_SubForum) {
            subForumItems.clear();
            listView.setAdapter(subListAdapter);
            loadForumListFromNet(subForum.url);
        } else if (currentLevel == LEVEL_MainForum) {
            mainForumItems.clear();
            loadMainList();
        } else if (currentLevel == LEVEL_FAVORITE) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    @Override
    public void onScrollStateChanged(AbsListView absListView, int scrollState) {
    }

    @Override
    public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (!swipeRefreshLayout.isRefreshing() && currentLevel == LEVEL_SubForum) {
            if (firstVisibleItem + visibleItemCount == totalItemCount) {
                int pageCount = (totalItemCount + 49) / 50;
                String url = subForum.url.replaceFirst("\\d+\\.html", String.valueOf(pageCount + 1) + ".html");
                loadForumListFromNet(url);
            }
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
        subForum = subChildren.get(position);
        loadSubList();
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }
}
