package education.mahmoud.quranyapp.feature.show_sura_ayas

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.ArraySet
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearSnapHelper
import butterknife.ButterKnife
import butterknife.OnClick
import education.mahmoud.quranyapp.App
import education.mahmoud.quranyapp.R
import education.mahmoud.quranyapp.data_layer.local.room.AyahItem
import education.mahmoud.quranyapp.data_layer.local.room.BookmarkItem
import education.mahmoud.quranyapp.data_layer.local.room.ReadLog
import education.mahmoud.quranyapp.feature.download.DownloadActivity
import education.mahmoud.quranyapp.feature.show_sura_ayas.PageAdapter.IBookmark
import education.mahmoud.quranyapp.feature.show_sura_ayas.PageAdapter.PageShown
import education.mahmoud.quranyapp.utils.Constants
import education.mahmoud.quranyapp.utils.Data
import education.mahmoud.quranyapp.utils.DateOperation
import kotlinx.android.synthetic.main.activity_show_ayahs.*
import kotlinx.android.synthetic.main.fragment_sura_list.*
import org.koin.android.ext.android.inject
import java.util.*

class ShowAyahsActivity : AppCompatActivity() {

    private val model: AyahsViewModel by inject()
    lateinit var pageAdapter: PageAdapter
    var pos = 0
    var pageList: List<Page> = listOf()
    var ayahsColor = 0
    var scrollorColor = 0
    private var lastpageShown = 1
    /**
     * list of pages num that contain start of HizbQurater
     */
    private lateinit var quraterSStart: List<Int>
    /**
     * hold num of pages that read today
     * will be update(in db) with every exit from activity
     */
    lateinit var pagesReadLogNumber: ArraySet<Int>
    /**
     * hold current date used to retrive pages and also with updating
     */
    private var currentDate: Long = 0
    /**
     * hold current date used to retrive pages and also with updating
     */
    private lateinit var currentDateStr: String
    /**
     * hold current readLog item used to retrive pages and also with updating
     */
    var readLog: ReadLog? = null

    private lateinit var toast: Toast

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_show_ayahs)
        ButterKnife.bind(this)


        pos = intent.getIntExtra(Constants.SURAH_INDEX, 1)
        pos = getStartPageFromIndex(pos)
        //region Description
        if (intent.hasExtra(Constants.SURAH_GO_INDEX)) {
            val surah = intent.getIntExtra(Constants.SURAH_GO_INDEX, 1)
            val ayah = intent.getIntExtra(Constants.AYAH_GO_INDEX, 1)
            Log.d(TAG, "onCreate: ayah  $ayah")
            pos = getPosFromSurahAndAyah(surah, ayah)
        } else if (intent.hasExtra(Constants.LAST_INDEX)) {
            pos = model.getLatestRead() // as it will be decreased
        } else if (intent.hasExtra(Constants.PAGE_INDEX)) { // case bookmark, go to by page
            pos = intent.getIntExtra(Constants.PAGE_INDEX, 1)
        } else if (intent.hasExtra(Constants.JUZ_INDEX)) {
            pos = intent.getIntExtra(Constants.JUZ_INDEX, 1)
            pos = getPageFromJuz(pos)
        }
        //endregion
        Log.d(TAG, "onCreate: $pos")
        initRV()

    }

    private fun addToReadLog(pos: Int) {
        pagesReadLogNumber.add(pos)
    }

    private fun getPosFromSurahAndAyah(surah: Int, ayah: Int): Int {
        return model.getPageFromSurahAndAyah(surah, ayah)
    }

    private fun getPageFromJuz(pos: Int): Int {
        return model.getPageFromJuz(pos)
    }

    private fun getStartPageFromIndex(pos: Int): Int {
        return model.getSuraStartpage(pos)
    }

    private fun initRV() {
        prepareColors()
        rvAyahsPages.setHasFixedSize(true)
        pageAdapter = PageAdapter(ayahsColor, scrollorColor)
        rvAyahsPages.adapter = pageAdapter
        rvAyahsPages.itemAnimator = DefaultItemAnimator()
        LinearSnapHelper().attachToRecyclerView(rvAyahsPages)
        pageAdapter.setPageShown(object : PageShown {
            override fun onDiplayed(pos: Int, holder: PageAdapter.Holder) { // items start from 0 increase 1 to get real page num,
                // will be used in bookmark
                lastpageShown = pos + 1
                // add page to read log
                addToReadLog(lastpageShown)
                /*    holder.topLinear.visibility = View.INVISIBLE
                    holder.BottomLinear.visibility = View.INVISIBLE*/
                // calculate Hizb info.
                val page = pageAdapter.getPage(pos)
                if (quraterSStart.contains(page.pageNum)) { // get last ayah to extract info from it
                    val ayahItem = page.ayahItems[page.ayahItems.size - 1]
                    var rub3Num = ayahItem.hizbQuarter
                    rub3Num-- // as first one must be 0
                    if (rub3Num % 8 == 0) {
                        showMessage(getString(R.string.juz_to_display, ayahItem.juz))
                    } else if (rub3Num % 4 == 0) {
                        showMessage(getString(R.string.hizb_to_display, rub3Num / 4))
                    } else {
                        var part = rub3Num % 4
                        part-- // 1/4 is first element which is 0
                        val parts = resources.getStringArray(R.array.parts)
                        showMessage(getString(R.string.part_to_display, parts[part], rub3Num / 4 + 1))
                    }
                }
            }
        })
        pageAdapter.setiBookmark(object : IBookmark {
            override fun onBookmarkClicked(item: Page) {
                val bookmarkItem = BookmarkItem()
                bookmarkItem.timemills = Date().time
                // get ayah to retrieve info from it
                val ayahItem = item.ayahItems[0]
                bookmarkItem.suraName = getSuraNameFromIndex(ayahItem.surahIndex)
                bookmarkItem.pageNum = item.pageNum
                Log.d(TAG, "onBookmarkClicked: " + bookmarkItem.pageNum)
                model.addBookmark(bookmarkItem)
                showMessage("Saved")
            }
        })
        // to preserver quran direction from right to left
        rvAyahsPages.layoutDirection = View.LAYOUT_DIRECTION_RTL
        pageAdapter.setiOnClick(IOnClick { pos ->
            // pos represent page and need to be updated by 1 to be as recyclerview
// +2 to be as Mushaf
            rvAyahsPages.scrollToPosition(pos + 1)
            //   addToReadLog(pos + 2);
        })
    }
    //</editor-fold>
    /**
     * @param surahIndex in quran
     * @return
     */
    private fun getSuraNameFromIndex(surahIndex: Int): String {
        return Data.SURA_NAMES[surahIndex - 1]
    }

    override fun onResume() {
        super.onResume()
        loadData()
        loadPagesReadLoge()
    }

    //<editor-fold desc="prepare colors">
    private fun prepareColors() { // check Night Mode
        if (model.nightModeState) { //            tvSuraNameShowAyas.setTextColor(getResources().getColor(R.color.ayas_color_night_mode));
            ayahsColor = resources.getColor(R.color.ayas_color_night_mode)
            scrollorColor = resources.getColor(R.color.bg_ays_night_mode)
        } else { //            tvSuraNameShowAyas.setTextColor(getResources().getColor(R.color.ayas_color));
            ayahsColor = resources.getColor(R.color.ayas_color)
            // check usesr color for background
            val col = model.backColorState
            when (col) {
                Constants.GREEN -> scrollorColor = resources.getColor(R.color.bg_green)
                Constants.WHITE -> scrollorColor = resources.getColor(R.color.bg_white)
                Constants.YELLOW -> scrollorColor = resources.getColor(R.color.bg_yellow)
            }
        }
    }

    private fun loadData() {
        pageList = (application as App).quranPages
        if (pageList.size >= 50) {
            // handler?.sendEmptyMessage(0)
            Log.d(TAG, "loadData: %%% ")
        } else {
            Log.d(TAG, "loadData: @@@@")
            Thread(Runnable {
                val pages: MutableList<Page> = ArrayList()
                var page: Page
                var ayahItems: List<AyahItem>
                for (i in 1..604) {
                    ayahItems = model.getAyahsByPage(i)
                    if (ayahItems.size > 0) {
                        page = Page()
                        page.ayahItems = ayahItems
                        page.pageNum = i
                        page.juz = ayahItems[0].juz
                        pages.add(page)
                    }
                }
                pageList = ArrayList(pages)
                //  handler?.sendEmptyMessage(0)
            }).start()
        }
        Thread(Runnable { generateListOfPagesStartWithHizbQurater() }).start()
    }

    private fun loadPagesReadLoge() {
        currentDate = DateOperation.getCurrentDateExact().time
        currentDateStr = DateOperation.getCurrentDateAsString()
        readLog = model.getLReadLogByDate(currentDateStr)
        readLog?.let {
            pagesReadLogNumber = it.pages
        }

    }

    /**
     * retrieve list of pages that contain start of hizb Quaters.
     */
    private fun generateListOfPagesStartWithHizbQurater() {
        quraterSStart = model.hizbQuaterStart
        // logData(quraterSStart);
    }

    private fun foundState() {
        spShowAyahs.visibility = View.GONE
        tvNoQuranData.visibility = View.GONE
        rvAyahsPages.visibility = View.VISIBLE
    }

    private fun notFound() {
        spShowAyahs.visibility = View.GONE
        tvNoQuranData.visibility = View.VISIBLE
        rvAyahsPages.visibility = View.GONE
    }

    private fun logData(quraterSStart: List<Int>) {
        for (integer in quraterSStart) {
            Log.d(TAG, "logData: $integer")
        }
    }

    private fun showMessage(message: String) {
        if (toast != null) {
            //    toast.cancel()
        }
        toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        //  toast.show()
    }

    @OnClick(R.id.tv_no_quran_data)
    fun onBOClicked() {
        val openAcivity = Intent(this@ShowAyahsActivity, DownloadActivity::class.java)
        startActivity(openAcivity)
    }

    override fun onStop() {
        super.onStop()
        model.addLatestread(lastpageShown)
        saveReadLog()
    }

    private fun saveReadLog() {
        readLog?.let {
            it.pages = pagesReadLogNumber
            // exception used to indicate its update or add case when update it will make exception as there is item in db
            try {
                model.addReadLog(it)
            } catch (e: Exception) {
                model.updateReadLog(it)
            }
        }
    }

    companion object {
        private const val TAG = "ShowAyahsActivity"
    }
}