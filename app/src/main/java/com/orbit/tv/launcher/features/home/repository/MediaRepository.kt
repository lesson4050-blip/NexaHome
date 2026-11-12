package com.orbit.tv.launcher.features.home.repository

import android.content.Context
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.features.home.model.MediaModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Repository fetching live, trending Movies and TV Series metadata.
 * Uses Cinemeta open metadata catalog with localized synopses & genres matching system language.
 */
class MediaRepository(private val context: Context) {

    private val moviesCacheFile = File(context.cacheDir, "cached_movies.json")
    private val seriesCacheFile = File(context.cacheDir, "cached_series.json")

    private fun isRussianLocale(): Boolean {
        val lang = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                context.resources.configuration.locales[0].language
            } else {
                @Suppress("DEPRECATION")
                context.resources.configuration.locale.language
            }
        } catch (_: Exception) {
            Locale.getDefault().language
        }
        return lang in listOf("ru", "be")
    }

    suspend fun getTrendingMovies(): List<MediaModel> = withContext(Dispatchers.IO) {
        // 1. Try fetching live catalog over the wire
        val liveData = fetchFromNetwork(MOVIES_URL)
        if (liveData != null) {
            try {
                moviesCacheFile.writeText(liveData)
            } catch (e: Exception) {}
            val parsed = parseCinemetaJson(liveData, isSeries = false)
            if (parsed.isNotEmpty()) return@withContext parsed
        }

        // 2. Fallback to cached JSON
        if (moviesCacheFile.exists()) {
            try {
                val cached = moviesCacheFile.readText()
                val parsed = parseCinemetaJson(cached, isSeries = false)
                if (parsed.isNotEmpty()) return@withContext parsed
            } catch (e: Exception) {}
        }

        // 3. Fallback to curated baseline
        getCuratedBaselineMovies()
    }

    suspend fun getTrendingSeries(): List<MediaModel> = withContext(Dispatchers.IO) {
        val liveData = fetchFromNetwork(SERIES_URL)
        if (liveData != null) {
            try {
                seriesCacheFile.writeText(liveData)
            } catch (e: Exception) {}
            val parsed = parseCinemetaJson(liveData, isSeries = true)
            if (parsed.isNotEmpty()) return@withContext parsed
        }

        if (seriesCacheFile.exists()) {
            try {
                val cached = seriesCacheFile.readText()
                val parsed = parseCinemetaJson(cached, isSeries = true)
                if (parsed.isNotEmpty()) return@withContext parsed
            } catch (e: Exception) {}
        }

        getCuratedBaselineSeries()
    }

    private fun fetchFromNetwork(urlString: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "OrbitTV-Launcher/1.0")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun translateGenres(genres: List<String>): String {
        val genreMap = mapOf(
            "Action" to context.getString(R.string.genre_action),
            "Adventure" to context.getString(R.string.genre_adventure),
            "Animation" to context.getString(R.string.genre_animation),
            "Biography" to context.getString(R.string.genre_biography),
            "Comedy" to context.getString(R.string.genre_comedy),
            "Crime" to context.getString(R.string.genre_crime),
            "Documentary" to context.getString(R.string.genre_documentary),
            "Drama" to context.getString(R.string.genre_drama),
            "Family" to context.getString(R.string.genre_family),
            "Fantasy" to context.getString(R.string.genre_fantasy),
            "History" to context.getString(R.string.genre_history),
            "Horror" to context.getString(R.string.genre_horror),
            "Music" to context.getString(R.string.genre_music),
            "Musical" to context.getString(R.string.genre_musical),
            "Mystery" to context.getString(R.string.genre_mystery),
            "Romance" to context.getString(R.string.genre_romance),
            "Sci-Fi" to context.getString(R.string.genre_scifi),
            "Sport" to context.getString(R.string.genre_sport),
            "Thriller" to context.getString(R.string.genre_thriller),
            "War" to context.getString(R.string.genre_war),
            "Western" to context.getString(R.string.genre_western)
        )
        return genres.map { genreMap[it] ?: it }.joinToString(", ")
    }

    private fun getRussianDescription(title: String, originalDesc: String): String {
        val t = title.lowercase(Locale.ROOT)
        return when {
            t.contains("dexter") ->
                "Декстер Морган выходит из комы и отправляется в Нью-Йорк, полный решимости найти сына Гаррисона и всё исправить. Но когда с расспросами появляется детектив Анхель Батиста, Декстер понимает, что тёмное прошлое настигает его слишком быстро."
            t.contains("obsession") ->
                "Уважаемый лондонский хирург вступает в страстный и разрушительный роман с невестой своего сына, ставя под угрозу семью, репутацию и карьеру."
            t.contains("hocus pocus") ->
                "Три эксцентричные ведьмы XVII века случайно воскресают в современном Салеме в ночь на Хэллоуин. Подросткам предстоит остановить их зловещие чары до рассвета."
            t.contains("verity") ->
                "Писательница Лоуэн соглашается закончить успешную книжную серию известного автора, но в её особняке находит шокирующую автобиографию с мрачными семейными тайнами."
            t.contains("halloween") ->
                "Культовое возвращение безжалостного Майкла Майерса в родной город Хэддонфилд в ночь Хэллоуина, несущее смертельную опасность для всех жителей."
            t.contains("backrooms") ->
                "Таинственные бесконечные лабиринты пустых офисных помещений с желтыми обоями и гудящими лампами, таящие необъяснимые опасные аномалии."
            t.contains("mobland") || t.contains("mob land") ->
                "В маленьком городке отчаявшийся местный житель решается на ограбление подпольного наркокартеля, втягивая шерифа в жестокую войну мафии."
            t.contains("carrie") ->
                "Застенчивая старшеклассница Кэрри с даром телекинеза подвергается насмешкам в школе, пока жестокая шутка на выпускном балу не оборачивается кровавой местью."
            t.contains("crystal lake") ->
                "Приквел к знаменитой франшизе «Пятница 13-е», раскрывающий происхождение кошмара и мрачную историю лагеря у Хрустального озера."
            t.contains("visionquest") || t.contains("vision quest") ->
                "Амбициозный спортсмен ставит перед собой невероятную цель победить непобедимого чемпиона штата по борьбе, преодолевая пределы сил."
            t.contains("dune") ->
                "Пол Атрейдес объединяется с Чани и фрименами, чтобы отомстить заговорщикам, уничтожившим его семью, и предотвратить катастрофическое будущее галактики."
            t.contains("oppenheimer") ->
                "История выдающегося физика Роберта Оппенгеймера, возглавившего Манхэттенский проект по созданию первой в мире атомной бомбы."
            t.contains("barbie") ->
                "Барби и Кен живут в идеальном беззаботном Барбиленде, пока внезапный кризис не вынуждает их отправиться в мир реальных людей."
            t.contains("spider-man") || t.contains("across the spider-verse") ->
                "Майлз Моралес отправляется в путешествие сквозь Мультивселенную, где встречает команду Людей-пауков, защищающих ткань мироздания."
            t.contains("house of the dragon") ->
                "Ожесточённая борьба за Железный трон внутри правящей династии Таргариенов, развязавшая разрушительную гражданскую войну «Танец Драконов»."
            t.contains("the last of us") ->
                "Спустя двадцать лет после краха человечества циничный выживший Джоэл соглашается тайно переправить 14-летнюю девочку Элли через опасную территорию США."
            t.contains("fallout") ->
                "Спустя 200 лет после ядерного апокалипсиса наивная жительница подземного бункера вынуждена выйти наружу в безжалостную и дикую Пустошь."
            t.contains("stranger things") ->
                "В тихом городке 1980-х загадочно исчезает мальчик, а его друзья натыкаются на правительственный заговор и жутких монстров из параллельного измерения."
            else -> originalDesc.ifBlank { context.getString(R.string.media_desc_unavailable) }
        }
    }

    private fun parseCinemetaJson(jsonStr: String, isSeries: Boolean): List<MediaModel> {
        val result = mutableListOf<MediaModel>()
        try {
            val root = JSONObject(jsonStr)
            val metas = root.optJSONArray("metas") ?: return emptyList()

            for (i in 0 until metas.length()) {
                val item = metas.optJSONObject(i) ?: continue
                val id = item.optString("id", "media_$i")
                val name = item.optString("name", "Untitled")
                val poster = item.optString("poster", "")
                if (poster.isBlank()) continue

                val genresArray = item.optJSONArray("genres")
                val genresList = mutableListOf<String>()
                if (genresArray != null) {
                    for (g in 0 until genresArray.length()) {
                        genresList.add(genresArray.optString(g))
                    }
                }
                val rawGenre = if (genresList.isNotEmpty()) translateGenres(genresList) else context.getString(R.string.genre_feature)

                val rating = item.optString("imdbRating", "")
                val badge = if (rating.isNotBlank()) "★ $rating" else if (isSeries) context.getString(R.string.badge_series) else context.getString(R.string.badge_movie)
                val rawDesc = item.optString("description", "")
                val finalDesc = if (isRussianLocale()) getRussianDescription(name, rawDesc) else rawDesc
                val year = item.optString("releaseInfo", "")

                result.add(
                    MediaModel(
                        id = id,
                        title = name,
                        posterUrl = poster,
                        badge = badge,
                        genre = rawGenre,
                        rawGenres = genresList,
                        rating = rating,
                        description = finalDesc,
                        releaseYear = year,
                        isSeries = isSeries,
                        deeplinkUri = "https://www.imdb.com/title/$id/"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun getCuratedBaselineMovies(): List<MediaModel> {
        val isRu = isRussianLocale()
        return listOf(
            MediaModel(
                id = "tt1160419",
                title = "Dune: Part Two",
                posterUrl = "https://images.metahub.space/poster/small/tt1160419/img",
                badge = "★ 8.6",
                genre = if (isRu) "Фантастика, Приключения" else "Sci-Fi, Adventure",
                rating = "8.6",
                description = if (isRu) "Пол Атрейдес объединяется с Чани и фрименами, чтобы отомстить заговорщикам, уничтожившим его семью, и предотвратить катастрофическое будущее галактики." else "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family.",
                releaseYear = "2024"
            ),
            MediaModel(
                id = "tt15398776",
                title = "Oppenheimer",
                posterUrl = "https://images.metahub.space/poster/small/tt15398776/img",
                badge = "★ 8.9",
                genre = if (isRu) "Биография, Драма, История" else "Biography, Drama, History",
                rating = "8.9",
                description = if (isRu) "История выдающегося физика Роберта Оппенгеймера, возглавившего Манхэттенский проект по созданию первой в мире атомной бомбы." else "The story of American scientist J. Robert Oppenheimer and his role in the development of the atomic bomb.",
                releaseYear = "2023"
            ),
            MediaModel(
                id = "tt1517268",
                title = "Barbie",
                posterUrl = "https://images.metahub.space/poster/small/tt1517268/img",
                badge = "★ 7.0",
                genre = if (isRu) "Приключения, Комедия" else "Adventure, Comedy",
                rating = "7.0",
                description = if (isRu) "Барби и Кен живут в идеальном беззаботном Барбиленде, пока внезапный кризис не вынуждает их отправиться в мир реальных людей." else "Barbie and Ken are having the time of their lives in the colorful and seemingly perfect world of Barbie Land.",
                releaseYear = "2023"
            ),
            MediaModel(
                id = "tt9362722",
                title = "Spider-Man: Across the Spider-Verse",
                posterUrl = "https://images.metahub.space/poster/small/tt9362722/img",
                badge = "★ 8.7",
                genre = if (isRu) "Мультфильм, Боевик" else "Animation, Action",
                rating = "8.7",
                description = if (isRu) "Майлз Моралес отправляется в путешествие сквозь Мультивселенную, где встречает команду Людей-пауков, защищающих ткань мироздания." else "Miles Morales catapults across the Multiverse, encountering a team of Spider-People charged with protecting its existence.",
                releaseYear = "2023"
            )
        )
    }

    private fun getCuratedBaselineSeries(): List<MediaModel> {
        val isRu = isRussianLocale()
        return listOf(
            MediaModel(
                id = "tt11198330",
                title = "House of the Dragon",
                posterUrl = "https://images.metahub.space/poster/small/tt11198330/img",
                badge = "★ 8.4",
                genre = if (isRu) "Боевик, Приключения, Драма" else "Action, Adventure, Drama",
                rating = "8.4",
                description = if (isRu) "Ожесточённая борьба за Железный трон внутри правящей династии Таргариенов, развязавшая разрушительную гражданскую войну «Танец Драконов»." else "An internal succession contest within House Targaryen at the height of its power.",
                releaseYear = "2022",
                isSeries = true
            ),
            MediaModel(
                id = "tt3581920",
                title = "The Last of Us",
                posterUrl = "https://images.metahub.space/poster/small/tt3581920/img",
                badge = "★ 8.7",
                genre = if (isRu) "Боевик, Приключения, Драма" else "Action, Adventure, Drama",
                rating = "8.7",
                description = if (isRu) "Спустя двадцать лет после краха человечества циничный выживший Джоэл соглашается тайно переправить 14-летнюю девочку Элли через опасную территорию США." else "After a global pandemic destroys civilization, a hardened survivor takes charge of a 14-year-old girl.",
                releaseYear = "2023",
                isSeries = true
            ),
            MediaModel(
                id = "tt12637874",
                title = "Fallout",
                posterUrl = "https://images.metahub.space/poster/small/tt12637874/img",
                badge = "★ 8.4",
                genre = if (isRu) "Боевик, Приключения, Фантастика" else "Action, Adventure, Sci-Fi",
                rating = "8.4",
                description = if (isRu) "Спустя 200 лет после ядерного апокалипсиса наивная жительница подземного бункера вынуждена выйти наружу в безжалостную и дикую Пустошь." else "In a future, post-apocalyptic Los Angeles brought about by nuclear decimation, citizens must live in underground bunkers.",
                releaseYear = "2024",
                isSeries = true
            ),
            MediaModel(
                id = "tt4574334",
                title = "Stranger Things",
                posterUrl = "https://images.metahub.space/poster/small/tt4574334/img",
                badge = "★ 8.7",
                genre = if (isRu) "Драма, Фэнтези, Ужасы" else "Drama, Fantasy, Horror",
                rating = "8.7",
                description = if (isRu) "В тихом городке 1980-х загадочно исчезает мальчик, а его друзья натыкаются на правительственный заговор и жутких монстров из параллельного измерения." else "When a young boy vanishes, a small town uncovers a mystery involving secret experiments and supernatural forces.",
                releaseYear = "2016",
                isSeries = true
            )
        )
    }

    companion object {
        private const val MOVIES_URL = "https://v3-cinemeta.strem.io/catalog/movie/top.json"
        private const val SERIES_URL = "https://v3-cinemeta.strem.io/catalog/series/top.json"
    }
}
