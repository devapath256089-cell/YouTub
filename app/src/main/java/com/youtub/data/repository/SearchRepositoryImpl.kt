/*
 * YouTub Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.youtub.data.repository

import com.youtub.domain.model.PaginatedList
import com.youtub.domain.model.SearchSort
import com.youtub.domain.model.VideoItem
import com.youtub.domain.model.SearchItem
import com.youtub.domain.model.PlaylistItem
import com.youtub.domain.model.UploadDateFilter
import com.youtub.domain.model.DurationFilter
import com.youtub.domain.repository.SearchRepository
import com.youtub.data.network.NewPipeInitializer
import com.youtub.utils.Constants
import com.youtub.utils.PTLog
import com.youtub.utils.VideoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.search.filter.FilterItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import javax.inject.Inject

class SearchRepositoryImpl @Inject constructor(
    private val initializer: NewPipeInitializer
) : SearchRepository {
    override suspend fun search(
        query: String,
        sort: SearchSort,
        uploadDate: UploadDateFilter,
        duration: DurationFilter
    ): PaginatedList<SearchItem> {
        initializer.ensureInitialized()
        return withContext(Dispatchers.IO) {
            try {
                val youtubeService = ServiceList.YouTube

                val extractor = buildSearchExtractor(youtubeService, query, sort, uploadDate, duration)
                PTLog.d("SearchRepository", "Searching for: $query (content=${if (sort == SearchSort.UPLOAD_DATE) "videos" else "all"})")
                extractor.fetchPage()

                val page = extractor.initialPage
                val items = page.items.mapNotNull { item ->
                    when (item) {
                        is StreamInfoItem -> SearchItem.Video(mapToVideoItem(item))
                        is ChannelInfoItem -> SearchItem.Channel(
                            id = item.url, // Usually full URL in NewPipe
                            name = item.name ?: "Unknown Channel",
                            thumbnailUrl = item.thumbnailUrl,
                            subscriberCount = item.subscriberCount,
                            description = item.description
                        )
                        is PlaylistInfoItem -> SearchItem.Playlist(
                            PlaylistItem(
                                id = VideoUtils.extractPlaylistId(item.url),
                                title = item.name ?: "Unknown Playlist",
                                thumbnailUrl = item.thumbnailUrl ?: "",
                                uploaderName = item.uploaderName ?: "Unknown Channel",
                                uploaderUrl = "", // fork PlaylistInfoItem lacks uploaderUrl
                                streamCount = item.streamCount
                            )
                        )
                        else -> null
                    }
                }

                PaginatedList(items, if (page.hasNextPage()) page.nextPage else null)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
                PaginatedList(emptyList(), null)
            }
        }
    }

    override suspend fun fetchNextPage(
        query: String,
        sort: SearchSort,
        uploadDate: UploadDateFilter,
        duration: DurationFilter,
        page: Page
    ): PaginatedList<SearchItem> {
        initializer.ensureInitialized()
        return withContext(Dispatchers.IO) {
            try {
                val youtubeService = ServiceList.YouTube

                val extractor = buildSearchExtractor(youtubeService, query, sort, uploadDate, duration)
                PTLog.d("SearchRepository", "Fetching next page for: $query")
                val nextPage = extractor.getPage(page)
                
                val items = nextPage.items.mapNotNull { item ->
                    when (item) {
                        is StreamInfoItem -> SearchItem.Video(mapToVideoItem(item))
                        is ChannelInfoItem -> SearchItem.Channel(
                            id = item.url,
                            name = item.name ?: "Unknown Channel",
                            thumbnailUrl = item.thumbnailUrl,
                            subscriberCount = item.subscriberCount,
                            description = item.description
                        )
                        is PlaylistInfoItem -> SearchItem.Playlist(
                            PlaylistItem(
                                id = VideoUtils.extractPlaylistId(item.url),
                                title = item.name ?: "Unknown Playlist",
                                thumbnailUrl = item.thumbnailUrl ?: "",
                                uploaderName = item.uploaderName ?: "Unknown Channel",
                                uploaderUrl = "", // fork PlaylistInfoItem lacks uploaderUrl
                                streamCount = item.streamCount
                            )
                        )
                        else -> null
                    }
                }

                PaginatedList(items, if (nextPage.hasNextPage()) nextPage.nextPage else null)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
                PaginatedList(emptyList(), null)
            }
        }
    }

    private fun mapToVideoItem(item: StreamInfoItem): VideoItem {
        val videoId = VideoUtils.extractVideoId(item.url)
        val uploadDate = item.textualUploadDate ?: item.uploadDate?.offsetDateTime()?.toLocalDate()?.toString() ?: ""
        val rawUploadDate = item.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: VideoUtils.parseTextualUploadDate(item.textualUploadDate)
        
        return VideoItem(
            id = videoId,
            title = item.name ?: "Unknown Title",
            thumbnailUrl = VideoUtils.getThumbnailForList(videoId),
            uploaderName = item.uploaderName ?: "Unknown Channel",
            uploaderUrl = item.uploaderUrl ?: "",
            uploaderThumbnailUrl = item.thumbnailUrl,
            viewCount = item.viewCount,
            subscriberCount = null,
            duration = item.duration,
            uploadDate = uploadDate,
            rawUploadDate = rawUploadDate
        )
    }

    /**
     * Build a SearchExtractor using the extractor's own FilterItem system.
     * The PipePipe fork replaced the old string-based filter API with FilterItem
     * objects that must come from the service's filter factory itself.
     */
    private fun buildSearchExtractor(
        youtubeService: StreamingService,
        query: String,
        sort: SearchSort,
        uploadDate: UploadDateFilter,
        duration: DurationFilter
    ): SearchExtractor {
        val queryHandler = youtubeService.searchQHFactory

        // Content filter: pick the extractor's own FilterItem by name
        val contentName = if (sort == SearchSort.UPLOAD_DATE) "videos" else "all"
        val contentItem = queryHandler.availableContentFilter
            .getFilterGroups().flatMap { it.filterItems.toList() }
            .firstOrNull { it.getName() == contentName }
            ?: throw IllegalStateException("Content filter '$contentName' not found")

        // Sort/date/duration items come from the content filter's variant
        val variantItems = queryHandler
            .getContentFilterSortFilterVariant(contentItem.getIdentifier())
            ?.getFilterGroups()?.flatMap { it.filterItems.toList() }
            ?: emptyList()

        fun pick(name: String?): FilterItem? =
            if (name == null) null else variantItems.firstOrNull { it.getName() == name }

        val sortItems = listOfNotNull(
            when (sort) {
                SearchSort.RELEVANCE -> pick("sort_relevance")
                SearchSort.VIEW_COUNT -> pick("sort_view")
                SearchSort.RATING -> pick("sort_rating")
                SearchSort.UPLOAD_DATE -> null // covered by the "videos" content filter
            },
            pick(
                when (uploadDate) {
                    UploadDateFilter.ALL -> null
                    UploadDateFilter.LAST_HOUR -> "past_hour"
                    UploadDateFilter.TODAY -> "past_day"
                    UploadDateFilter.THIS_WEEK -> "past_week"
                    UploadDateFilter.THIS_MONTH -> "past_month"
                    UploadDateFilter.THIS_YEAR -> "past_year"
                }
            ),
            pick(
                when (duration) {
                    DurationFilter.ALL -> null
                    DurationFilter.SHORT -> "short_video"
                    DurationFilter.MEDIUM -> null // extractor has no medium option
                    DurationFilter.LONG -> "long_video"
                }
            )
        )

        return youtubeService.getSearchExtractor(
            query,
            listOf(contentItem),
            if (sortItems.isEmpty()) null else sortItems
        )
    }

    override suspend fun getSearchSuggestions(query: String): List<String> {
        initializer.ensureInitialized()
        return withContext(Dispatchers.IO) {
            try {
                val youtubeService = ServiceList.YouTube
                youtubeService.suggestionExtractor.suggestionList(query)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
}
