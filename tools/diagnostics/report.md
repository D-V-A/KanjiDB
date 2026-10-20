# JMdict / dictionary.db: диагностическое исследование

Дата: 2026-10-13. Локальный JMdict XML: `JMdict created: 2026-09-16`. Все основные модели/counts относятся к **импортированной DB**, а source distribution отмечена отдельно. Это анализ конкретного локального snapshot, не всего актуального JMdict.

SQLite открывалась `mode=ro` и `query_only=ON`. Импортер не запускался. SHA-256 DB/XML до и после совпали. Production code, schema, assets и версии не изменены. Предыдущие незакоммиченные изменения milestone сохранены.

Методика: длины в Unicode code points; exact written/reading — без normalization. English gloss TRIM — удаление ASCII spaces, как SQLite TRIM; CI — дополнительно Python casefold. Meaning overlap = `|A∩B| / max(|A|,|B|)` по полным CI gloss strings, не семантическая similarity. Одинаковые gloss strings не означают одинаковые senses.

## 1. Executive summary

- Model A: **231 241** entities; B: **226 836**, уменьшение **1.90%**; C: **171 585**, уменьшение **25.80%**. Это counts identities, не прогноз физического удаления rows.
- Exact-written duplicate spellings: **3 685**. Readings чаще различаются: у 3 356 spelling groups все entry reading sets попарно непересекающиеся.
- Same-entry reading equality не универсальна: **2 738** multi-written entries имеют разные exact reading sets, **1 656** — хотя бы одну пару без общего exact reading.
- Транзитивный Model C имеет выраженный риск: максимум **61 source entry, 48 written, 77 readings**; туда одновременно входят 男, 女, 妻, 俺, 側. Это не один очевидный lexical word.
- Variants не преимущественно ограничены короткими словами: **56.14%** multi-written entries содержат форму длиной ≥4, **15.52%** — ≥6; maximum 27.

## 2. Current DB counts

| Метрика | Count |
| --- | --- |
| Импортированные JMdict entries / distinct entry_id | 175 670 |
| word_form rows | 246 161 |
| distinct written | 226 836 |
| distinct reading | 160 472 |
| distinct (entry_id,written) | 231 241 |
| distinct (written,reading) | 245 818 |
| word_meaning rows (все English) | 583 285 |
| word_kanji rows | 619 220 |

| Значение | Количество |
| --- | --- |
| all_entries | 218 785 |
| no_k_ele | 41 236 |
| fully_filtered | 43 115 |
| importer_processed_counter | 177 549 |
| processed_but_no_imported_forms | 1 879 |
| imported_entries_with_stagr | 708 |
| imported_entries_with_re_restr | 2 740 |
| imported_entries_with_stagk | 455 |

Importer `entry_count` не считает entries без k_ele. 177 549 — entries с written, а не все прочитанные XML entries; из них 1 879 не дали valid imported form. 43 115 полностью отфильтровано. Набор воспроизведённых из XML импортированных entry_id точно совпал с DB. 208 исходных written форм отсутствуют внутри импортированных entries (например ５); DB-derived variants не включают их.

Текущая schema: `word_form` UNIQUE(entry_id,written,reading); `word_meaning` UNIQUE(word_form_id,sense_index,language,meaning); `word_kanji` PK(word_form_id,position). Importer сохраняет reading_order, common/priority; применяет re_restr, исключает re_nokanji и written без известного KANJIDIC character; glosses применяются только при совпадении stagk/stagr. Эти связи не восстанавливаются безопасно простым UNION всех glosses.

## 3. Exact-written duplicates и meaning duplication

3 685 distinct written связывают 6 924 distinct source entries и 8 090 (entry,written) memberships. Model B устраняет 4 405 identities (1.90%); word_form после простого collapse identical (written,reading) станет 245 818, только −343 rows (0.14%). Разные readings/senses всё равно надо хранить.

Distribution number of entry_ids per written:
| Значение | Количество |
| --- | --- |
| 2 | 3 175 |
| 3 | 391 |
| 4 | 74 |
| 5 | 26 |
| 6 | 8 |
| 7 | 4 |
| 8 | 4 |
| 9 | 1 |
| 10 | 1 |
| 14 | 1 |

Readings/meaning exact-string classifications (meaning categories mutually exclusive):
| Значение | Количество |
| --- | --- |
| pairwise_disjoint_readings | 3 356 |
| partial_meaning_overlap_below_075 | 1 014 |
| no_shared_exact_gloss | 2 379 |
| identical_meaning_sets_ci | 210 |
| some_reading_overlap | 101 |
| at_least_one_meaning_pair_overlap_ge_075 | 82 |
| identical_reading_sets | 228 |

В duplicate-written subsets: 36 406 raw English gloss rows -> 28 471 per-written TRIM unique glosses: 7 935 duplicates, **21.80%**. CI: 28 464, duplicates 21.82%. Это включает копии между readings/senses. Если сначала дедуплицировать каждый (entry,written), было 31 457; собственно cross-entry повторов 2 986 (9.49%). Ни один из этих counts не разрешает потерю sense provenance.

### Обязательные примеры

**五**

- 1268060 **五** [い, いつ, ご] — 5; five
- 2747970 **五** [ウー] — five

**男**

- 1419990 **男** [おっこ, おとこ] — bloke; boyfriend; chap; fellow; guy; male; male lover; man; manliness; manly honor; manly honour; manly reputation
- 2742850 **男** [おのこ] — boy; man
- 2742870 **男** [お] — brave; greater; heroic; husband; larger (of the two); male; man; manly
- 2742890 **男** [なん] — son
- 2843485 **男** [だん] — baron; male; man; masculine noun; son

**前**

- 1387210 **前** [さき] — (the way) ahead; address; ahead (of); before; beyond; continuation; destination; end; first; former; front; future; head (of a line); last; nozzle; place where you do something; point; previous; prior; recent; remaining part; rest (e.g. of a story); the other party; tip
- 1392570 **前** [ぜん] — before; earlier; ex-; former; last (i.e. immediately preceding); pre- (e.g. premodern); previous
- 1392580 **前** [まえ] — (a) prior; (in the) presence (of); (minutes) to (the hour); (the) front; ago; ahead; before; before (e.g. a building); breast (of a coat, kimono, etc.); criminal record; earlier; first (e.g. half); fore; former (e.g. example); forward; front (of one's body or clothing); frontal part; head (e.g. of a line); helping; in front (of someone); in front (of); portion; previous (e.g. page); previous conviction; previously; prior; prior (e.g. engagement); private parts; privates

**日**

- 1463770 **日** [ひ] — (past) days; (the) day; case (esp. unfortunate); date; day; daylight; days; daytime; deadline; event; sun; sunlight; sunshine; time (e.g. of one's childhood)
- 2083100 **日** [にち] — Japan; Sunday; counter for days; nth day (of the month)
- 2083110 **日** [か] — counter for days; day of month
- 2856786 **日** [ち, んち] — counter for days; nth day (of the month)

### 40 реальных duplicate-written примеров

**私**

- 1311110 **私** [わたし, ワタシ] — I; me
- 1311125 **私** [あたくし, あたし, アタシ] — I; me
- 1347580 **私** [わらわ] — I; me
- 2015370 **私** [わし] — I; me
- 2079310 **私** [あっし, わっし] — I; me
- 2217330 **私** [わい, ワイ] — I; me; you
- 2217340 **私** [わたい] — I; me
- 2728300 **私** [し] — personal matter; private affairs
- 2842390 **私** [わたくし, ワタクシ] — I; confidentiality; me; partiality; personal (affairs, etc.); private; secrecy; selfishness
- 2845454 **私** [わっち] — I; me
- 2858221 **私** [わて] — I; me
- 2858384 **私** [あたい] — I; me
- 2858397 **私** [あて] — I; me
- 2864027 **私** [わちき] — I; me

**頭**

- 1450690 **頭** [とう] — counter for helmets, masks, etc.; counter for insects in a collection; counter for large animals (e.g. head of cattle)
- 1582310 **頭** [あたま, かしら] — beginning; boss; brains; captain; chief; deposit; down payment; hair (on one's head); head; intellect; leader; mind; pair; person; start; tip; top; top structural component of a kanji
- 2252670 **頭** [がしら] — head of ...; the moment that ...; top of ...
- 2252700 **頭** [どたま] — bean; dome; head; nob; noggin
- 2791490 **頭** [かぶし] — shape of one's head
- 2791510 **頭** [こうべ] — head
- 2843246 **頭** [ず] — head
- 2856438 **頭** [かぶり] — head
- 2856439 **頭** [つむり] — head
- 2856440 **頭** [つむ] — head

**汝**

- 1631650 **汝** [うぬ] — blockhead!; you
- 2015140 **汝** [なんじ] — you
- 2174410 **汝** [い, しゃ] — you
- 2174420 **汝** [いまし] — you
- 2174430 **汝** [し] — oneself; that; themself; you
- 2174450 **汝** [な] — I; you
- 2174460 **汝** [なむち, なれ, まし] — you
- 2174490 **汝** [みまし] — you
- 2869001 **汝** [そなた] — that way; there; you

**家**

- 1191730 **家** [いえ] — dwelling; family; family name; home; house; household; lineage; residence
- 1191740 **家** [うち] — house; one's family; one's home; one's house; one's household
- 1191750 **家** [け] — family; house
- 1457730 **家** [うち] — (out) of; amidst; among; amongst; between; during; in (secret, chaos, poverty, etc.); in the course of; inner thoughts; inside; one's feelings; while (e.g. one is young); with (e.g. success); within; within (e.g. a day); within oneself
- 2082160 **家** [や] — house; restaurant; roof; shop; someone who sells (something) or works as (something); someone with a (certain) personality trait; store
- 2220280 **家** [か] — -er; -ist
- 2220300 **家** [んち] — 's home; 's house
- 2858378 **家** [ち] — 's home; 's house

**己**

- 1266890 **己** [おのれ] — I; by oneself (itself, etc.); interjection expressing anger or chagrin; me; oneself (itself, etc.); you
- 1576870 **己** [おれ] — I; me
- 1578530 **己** [き, つちのと] — 6th in rank; sixth sign of the Chinese calendar
- 1631650 **己** [うぬ] — blockhead!; you
- 2080360 **己** [うら, おら] — I; me
- 2084820 **己** [おどれ, おんどれ] — you
- 2174450 **己** [な] — I; you
- 2735810 **己** [おぬ, おの] — I; myself; oneself; you

**直**

- 1430580 **直** [なお] — common; doing nothing; ordinary; straight
- 1430590 **直** [ひた] — earnestly; exactly; immediately
- 1581790 **直** [じき] — before long; cash transaction; close; direct; in a moment; nearby; shortly; soon; spot transaction
- 1581795 **直** [ちょく] — being on duty (in a factory); candid; direct; easygoing; frank; friendly; honest; lighthearted; night duty; night watch; shift; straightforward
- 2264640 **直** [あたい, あたえ] — Atai (post-Taika reform hereditary title often given to regional administrators)
- 2406000 **直** [じか] — direct
- 2406710 **直** [ただ] — direct; straight
- 2836242 **直** [ね] — cost; merit; price; value; worth

**首**

- 1592270 **首** [くび] — dismissal (from a job); firing; head; neck; sacking
- 1653340 **首** [しるし] — decapitated head of an enemy
- 1994890 **首** [しゅ] — counter for songs and poems
- 2251080 **首** [つかさ] — chief; head; office; official
- 2265230 **首** [おうと, おびと] — Obito (hereditary title, often given to powerful regional families)
- 2712010 **首** [このかみ] — eldest son; head of a clan; head of a region; older brother; older person; older sister; skilled craftsman
- 2791500 **首** [かしら] — head (of a doll)
- 2791510 **首** [こうべ] — head

**主**

- 1324940 **主** [おも] — chief; important; main; main secondary or supporting role (in kyogen); principal
- 1324950 **主** [しゅ] — (one's) employer; (one's) lord; (one's) master; Lord; chief; chief part; first consideration; head; leader (of a country, group, etc.); main thing; primary object; principal aim
- 1324960 **主** [ぬし] — doer (of a deed); guardian spirit (e.g. long-resident beast, usu. with mystical powers); head (of a household, etc.); husband; leader; long-time resident (or employee, etc.); master; owner; proprietor; proprietress; subject (of a rumour, etc.); you
- 2427530 **主** [にし, のし] — you
- 2427540 **主** [す] — honorific (or familiar) suffix used after a name
- 2427550 **主** [あるじ] — entertaining someone as one's guest; head (of a household); landlady; landlord; master (of a servant); proprietor (of a store); proprietress
- 2860409 **主** [しゅう] — (one's) employer; (one's) lord; (one's) master

**徒**

- 1444420 **徒** [と] — company; gang; party; person; set
- 1530510 **徒** [むだ] — futility; idleness; pointlessness; uselessness; waste
- 1538900 **徒** [ただ] — as is; but; common; except; free; free of charge; however; just; merely; nevertheless; only; ordinary; safe; simply; unaffected; usual
- 1615140 **徒** [あだ] — frivolous; futile; transient; vain
- 2249010 **徒** [ず] — imprisonment (for 1-3 years; third most severe of the five ritsuryō punishments)
- 2611940 **徒** [かち] — foot soldier (Edo period); going on foot; samurai on foot; walking
- 2681740 **徒** [いたずら] — aimless; idle; useless; vain

**灯**

- 1582290 **灯** [ひ] — lamp; light
- 1586210 **灯** [あかり] — (a) light; lamp; lights
- 2519560 **灯** [あかし] — lamp; light (esp. as a religious offering)
- 2519570 **灯** [とう] — counter for electric lights; lamp; light
- 2519580 **灯** [ともし] — baiting deer with a lighted torch; lamp; light
- 2519590 **灯** [とぼし] — lamp; light
- 2850328 **灯** [ともしび] — lamp; light; torch

**生**

- 1378440 **生** [き] — crude; pure; raw; undiluted
- 1378450 **生** [なま] — as it is; bareback; blank (e.g. disk); cash; crude; draft beer; draught beer; fresh; green; half-; half-baked; half-hearted; impudence; incomplete; inexperienced; insufficient; just a little; live (i.e. not recorded); natural; partially; perfunctory; raw; sauciness; semi-; somehow; somewhat; tipsiness; uncooked; unedited; unpasteurized beer; unpolished; unprocessed; unprotected (sex); unused; vaguely
- 2036160 **生** [うぶ] — green; inexperienced (in life experience); inexperienced (in love); innocent; naive; simple; unsophisticated; wet behind the ears
- 2088240 **生** [しょう, せい] — I; life; living; me; myself; student
- 2416400 **生** [いく] — lively; virile; vital
- 2416410 **生** [ふ] — area of thick growth (of trees, grass, etc.)
- 2416420 **生** [なまり] — boiled and half-dried bonito

**一**

- 1160790 **一** [いち] — 1; a (single); ace; beginning; best; bottom string (on a shamisen, etc.); first; foremost; one; one (of many); start
- 1160820 **一** [ひとつ] — (not) even; for one thing; just (e.g. "just try it"); one; one type of; only; some kind of
- 2747940 **一** [イー] — one
- 2821440 **一** [いっち] — best; most
- 2821500 **一** [ひ, ひい, ひと] — one
- 2843147 **一** [いつ] — one; same (mind, path, etc.)

**下**

- 1184140 **下** [した] — below; beneath; bottom; down; inferiority; just after; one's inferior (i.e. one's junior); preliminary; preparatory; right after; trade-in; under; underneath; younger (e.g. daughter)
- 2004390 **下** [もと] — (someone's) location; (someone's) side; beneath; in (e.g. the name of ...); on (the promise, condition, assumption, etc. that ...); under (a flag, the sun, etc.); under (guidance, supervision, rules, the law, etc.); with (e.g. one blow)
- 2080200 **下** [げ] — inferiority; lowness (of degree, value, etc.); second volume (of two); third volume (of three)
- 2080210 **下** [しも] — bottom; dirty (e.g. dirty jokes, etc.); end; far from the imperial palace (i.e. far from Kyoto, esp. of western Japan); feces (faeces); lower half (of the body, esp. the privates); lower part; lower reaches (of a river); menses; urine
- 2159100 **下** [か] — during (war, occupation, etc.); under (influence, control, conditions, etc. of)
- 2867124 **下** [さがる] — below; south of

**妻**

- 1294330 **妻** [つま] — accompaniment; garnish (esp. one served with sashimi); side (remark); trimmings; wife
- 2742880 **妻** [め] — female; smaller (of the two); weaker; wife; woman
- 2746040 **妻** [さい] — one's wife
- 2746070 **妻** [つま] — edge; end; gable; gable wall; tip
- 2843131 **妻** [そばめ] — close female servant; concubine; kept woman; mistress
- 2852371 **妻** [つま] — dear; honey; my dear

**子**

- 1307770 **子** [こ] — (one's) child; -er (often of young women); bird egg; child; interest; kid; new share; offshoot; offspring; player who is not a dealer; teenager; young (animal); young (non-adult) person; young geisha; young prostitute; young woman; youngster
- 1307780 **子** [ね] — eleventh month of the lunar calendar; hour of the Rat (around midnight, 11pm to 1am, or 12 midnight to 2am); north; the Rat (first sign of the Chinese zodiac)
- 1320810 **子** [み] — (in broth) pieces of meat, vegetable, etc.; content; fruit; nut; seed; substance
- 2216440 **子** [し] — -er (i.e. man who spends all his time doing ...); child (esp. a boy); counter for played stones; founder of a school of thought (esp. Confucius); master; masters and philosophers (categorization of Chinese classical literature); viscount; you
- 2427540 **子** [す] — honorific (or familiar) suffix used after a name
- 2744640 **子** [こう] — child; interest

**小**

- 2083540 **小** [しょう] — elementary school; inferior; junior; pee; piss; short month (i.e. having fewer than 31 days); small item; smaller (of two things, places, etc. with the same name); smallness; unit of field area (approx. 400 sq m); urination; urine; younger (of two people with the same name)
- 2153770 **小** [こ] — just about; little; minor; petty; slight; slightly less than; small; somehow; somewhat
- 2268350 **小** [お] — a bit; familiar prefix; narrow; slightly; small
- 2268910 **小** [ささ, さざ] — little; small
- 2564200 **小** [ぐゎー] — dear; little; small
- 2729810 **小** [さ] — narrow; thin

**後**

- 1269320 **後** [あと] — after; after one's death; also; behind; descendant; heir; in addition; later; left; more (e.g. five more minutes); past; previous; rear; remainder; successor; the rest
- 1269330 **後** [のち] — after one's death; afterwards; descendant; future; later
- 1269410 **後** [うしろ] — back; behind; rear
- 1358760 **後** [しり] — behind; bottom; buttocks; consequence; end; hips; last place; rump; undersurface
- 2147630 **後** [ご] — after
- 2862495 **後** [しりえ] — back; behind; rear

**節**

- 1386140 **節** [せつ] — clause; integrity; knot (nautical mile per hour); node (of a plant stem); occasion; one's principles; paragraph; passage; season; section (of a literary work or piece of music); section (taxonomic rank); stanza; term; time; verse
- 1386150 **節** [ノット] — knot (nautical mile per hour)
- 1386160 **節** [ふし, フシ] — (notable) point; joint; knob; knot (in wood); knuckle; melody; node; node (of a plant stem); part; tune
- 1386170 **節** [ぶし] — characteristic way of speaking
- 2255020 **節** [よ] — space between two nodes (on bamboo, etc.)
- 2843303 **節** [せち] — season; seasonal banquet; seasonal feast; seasonal festival; time of the year

**縁**

- 1013030 **縁** [ゆかり] — affinity; connection (to a person, place, etc.); relation
- 1177490 **縁** [え, えに, えにし, えん] — affinity; bond; chance (to meet someone and start a relationship); connection; destiny (esp. as a mysterious force that binds two people together); family ties; fate; link; narrow open-air veranda; opportunity; pratyaya (indirect conditions, as opposed to direct causes); relationship (e.g. between two people)
- 1177500 **縁** [ふち] — brim; brink; edge; rim
- 1541760 **縁** [よし] — cause; reason; significance
- 1922790 **縁** [よすが] — aid; clue; means; memento; relative; reminder; someone to rely on; something to rely on; way
- 2056370 **縁** [へり] — brim; edge (of a river, woods, etc.); edging; fabric border (of a tatami mat, etc.); fringe; hem; margin; rim; selvage; shoulder (of a road)

**一日**

- 1576260 **一日** [いちじつ, いちにち] — all day (long); first day of the month; from morning till night; one day; the whole day
- 2225030 **一日** [つきたち] — first day of the month; first ten days of the lunar month
- 2225040 **一日** [ついたち] — first day of the month; the first of the month
- 2852123 **一日** [ひとひ] — 1st day of the month; all day (long); one day; the whole day
- 2863119 **一日** [いっぴ] — first day of the month

**両**

- 2080720 **両** [りょう] — both (hands, parents, sides, etc.); counter for carriages (e.g. in a train); counter for suits of clothing, sets of armor, etc.; counter for vehicles; pre-Meiji unit of currency, orig. the value of one ryō of gold; ryō; tael; traditional measure of fabric, 2 tan; traditional unit of weight (for gold, silver and drugs), 4-5 monme, 15-19 g; unit of weight under the ritsuryō system, 1/16 kin, 42-43 g
- 2701150 **両** [もろ] — both
- 2832843 **両** [りゃん] — samurai; two
- 2858788 **両** [リャン] — two
- 2859856 **両** [テール] — tael (Chinese unit of weight and currency)

**中**

- 1423310 **中** [なか] — amid; among; average; center; centre; during; gap; inside; interior; interval; mean; medium grade; medium quality; middle; midst; red-light district; second (of three, e.g. sons, volumes); while; within
- 1457730 **中** [うち] — (out) of; amidst; among; amongst; between; during; in (secret, chaos, poverty, etc.); in the course of; inner thoughts; inside; one's feelings; while (e.g. one is young); with (e.g. success); within; within (e.g. a day); within oneself
- 1620400 **中** [ちゅう] — China; among; average (grade, level, etc.); currently (doing); during; in; in (the course of); in the middle of; in the process of; junior high school; medium (size); middle; middle school; middle way; moderation; out of (e.g. ten people); second volume (of three); throughout; under (e.g. construction, investigation); while; within; within (a period)
- 2083570 **中** [じゅう, ぢゅう] — (all) through (e.g. the night); all (of a group); all ... long; all over; by the end of; in the course of (e.g. today); the whole (family, school, etc.); throughout (e.g. the world); throughout (e.g. the year); within (e.g. the week)
- 2754230 **中** [チュン] — red dragon tile; winning hand with a pung (or kong) of red dragon tiles

**大**

- 2082140 **大** [おお] — big; broad; final; general; great; heavy; important; large; loud; major; older; rough; senior; serious; ultimate
- 2082450 **大** [だい] — -sized; as big as; big; distinguished; eminent; excrement; faeces; feces; great; huge; important; large; large (e.g. serving size); large option; long month (i.e. having 31 days); major; prominent; serious; severe; stool; the size of; university; vast
- 2427850 **大** [おおき] — a great deal; grand; great; greater (of equal court ranks); large; senior; upper; very much
- 2427860 **大** [おおい] — greater (of equal court ranks); senior; upper
- 2839018 **大** [たい] — nth year in the Taishō era (1912.7.30-1926.12.25)

**大人**

- 1414170 **大人** [おとな] — adult; grown-up
- 1785170 **大人** [たいじん] — adult; exalted person; giant; great person; magnanimous person; person of virtue
- 2860067 **大人** [だいにん] — adult
- 2864579 **大人** [うし] — exalted person; feudal lord; nobleman; person of high education
- 2866562 **大人** [おおびと] — giant

**夫**

- 1496480 **夫** [おっと] — husband
- 2742870 **夫** [お] — brave; greater; heroic; husband; larger (of the two); male; man; manly
- 2754880 **夫** [そ] — he; she; that; that person
- 2852371 **夫** [つま] — dear; honey; my dear
- 2870787 **夫** [ふ] — (working) man; laborer

**女**

- 1344370 **女** [すけ] — assistance; babe; broad; chick; help; helper
- 1344930 **女** [おんな] — (someone's) woman; female; female lover; girlfriend; mistress; woman
- 1344940 **女** [じょ] — Chinese "Girl" constellation (one of the 28 mansions); daughter; feminine noun; girl; woman
- 2742880 **女** [め] — female; smaller (of the two); weaker; wife; woman
- 2860163 **女** [おみな] — woman

**実**

- 1320800 **実** [じち, じつ] — (good) result; content; fidelity; honesty; reality; sincerity; substance; truth
- 1320810 **実** [み] — (in broth) pieces of meat, vegetable, etc.; content; fruit; nut; seed; substance
- 1580555 **実** [まこと] — absolutely; actually; fact; faithfulness; good faith; honesty; indeed; quite; reality; really; sincerity; truly; truth; very
- 2216570 **実** [さね] — clitoris; core; heart; kernel; pip; pit; seed (of a fruit); stone; tongue (projecting strip on a board that fits into a groove on another board)
- 2216580 **実** [そうじみ, ただみ, むざね] — genuine article; real thing; said person; the real person

**徒人**

- 2681680 **徒人** [ただうど, ただひと, ただびと] — ordinary person; untitled individual
- 2681700 **徒人** [いたずらびと] — dead person; down-and-out person; useless fellow
- 2681710 **徒人** [かちど, かちびと] — person going by foot; walker
- 2681720 **徒人** [ずにん] — prisoner (for one to three years under the ritsuryō system)
- 2681730 **徒人** [あだびと] — capricious person; fickle person; stylish person

**御**

- 1270190 **御** [ご] — honorific suffix; honorific/polite/humble prefix
- 2259690 **御** [ぎょ] — honorific affix; honorific prefix
- 2259710 **御** [み] — august; beautiful
- 2259720 **御** [おおむ, おおん] — august (in ref. to the emperor or the gods); divine; imperial
- 2826528 **御** [お, おん] — honorific/polite/humble prefix

**擦る**

- 1298910 **擦る** [こする] — to keep getting mileage out of; to mash (a button); to milk; to repeatedly reuse (a joke, a topic of conversation, etc.); to rub; to scrape; to scrub; to spam (a move)
- 1523790 **擦る** [さする] — to massage; to pat; to rub; to stroke
- 1595910 **擦る** [する] — to chafe; to file; to frost (glass); to lose money (e.g. by gambling); to rub; to run through (one's fortune); to squander (one's money); to strike (match)
- 1636530 **擦る** [かする] — to exploit; to graze (e.g. bullet); to scratch; to squeeze; to take a percentage; to touch lightly
- 2086270 **擦る** [なする] — to daub; to lay the blame on; to rub in; to smear on; to spread on

**故**

- 1265060 **故** [ふる] — old; previous; secondhand item; used; used item
- 1267110 **故** [こ] — the deceased; the late
- 1267120 **故** [ゆえ] — as a result of; because of; cause; circumstances; due to; grounds; reason
- 1328690 **故** [ことさら] — deliberately; designedly; especially; intentionally; on purpose; particularly
- 2219590 **故** [もと] — before; earlier times; ex-; former; formerly; one-time; originally; past; previous state; previously; the past

**文**

- 1505080 **文** [ふみ] — book; learning; letter; literary arts; mail; note; scholarship; writings
- 1505090 **文** [ぶん] — academia; composition; literature; sentence; statement; text; the literary arts (as opposed to the military arts); writings
- 1957010 **文** [もん] — (family) crest; coat of arms; figure; pattern; playing card suit (in karuta)
- 2034990 **文** [あや] — cat's cradle; design; figure; figure (of speech); lease rod (in a loom); minor market fluctuation; plan; plot; style (of writing); technical correction
- 2145190 **文** [もん] — character; incantation; letter; mon; one-thousandth of a kan (unit of currency 1336-1870); scripture; sentence; traditional unit used for shoe and sock sizes (approx. 2.4 cm)

**方**

- 1516925 **方** [かた] — care of ...; direction; gentleman; lady; manner of; method of; person; person in charge of ...; side (e.g. "on my mother's side"); way; way of
- 1516930 **方** [ほう] — area (in a particular direction); category; direction; field (of study, etc.); indicates one side of a comparison; length (of each side of a square); manner; means; method; one's part; side; side (of an argument, etc.); type; way
- 1545790 **方** [さま] — Miss; Mr; Mrs; Ms; appearance; makes a word more polite (usu. in fixed expressions); manner; situation; state
- 2253290 **方** [がた] — Mesdames; Sirs; about; around (the time that, etc.)
- 2254960 **方** [へ] — coast; seashore; seaside; suffix used as a rough indicator of location, direction, time, etc.; surrounding area; vicinity

**株**

- 1208920 **株** [かぶ] — (clump of) roots; KK; corporation; goodwill (of a business); kabushiki kaisha; one's forte; plant with attached roots or stem; popularity; privilege (that comes with a role); reputation; share; standing; stock; stock company; strain (of bacteria, etc.); stump
- 1279450 **株** [くい] — picket; pile; post; stake; stump
- 2645430 **株** [しゅ] — counter for trees; stump
- 2645440 **株** [くいせ, くいぜ] — stump
- 2737710 **株** [かぶた] — stump; useless item

**歩**

- 1514290 **歩** [ふ] — pawn
- 1514300 **歩** [ほ] — counter for steps; step; stride
- 1514330 **歩** [あゆみ] — advance; course (of history, one's life, etc.); development; history; pace; pitch (of a screw, etc.); progress; step; walking
- 2145430 **歩** [ぶ] — bu; commission; exactly; percentage; precisely; traditional unit of area, approx. 3.31 square metres
- 2611940 **歩** [かち] — foot soldier (Edo period); going on foot; samurai on foot; walking

**片**

- 1516920 **片** [かた] — few; fragmentary; imperfect; incomplete; little; matters; off-centre; one (of a pair); problem; question; remote; side
- 2003800 **片** [ペンス] — pence (monetary unit)
- 2452350 **片** [へん] — counter for scraps, fragments, petals, etc.
- 2452360 **片** [ひら] — counter for thin, flat things (petals, leaves, etc.)
- 2509520 **片** [びら] — bill; flier; flyer; handbill; leaflet; placard; poster

**甲**

- 1204740 **甲** [よろい] — armor; armour
- 1209030 **甲** [かぶと] — headpiece; helmet (of a warrior)
- 1578730 **甲** [こう] — 1st in rank; back of hand; carapace; grade A; instep; plaintiff (label in legal documents); shell; the A party (e.g. in a contract); the first party
- 2240200 **甲** [かん] — high note; treble range (in Japanese music)
- 2397580 **甲** [きのえ] — first sign of the Chinese calendar

**男**

- 1419990 **男** [おっこ, おとこ] — bloke; boyfriend; chap; fellow; guy; male; male lover; man; manliness; manly honor; manly honour; manly reputation
- 2742850 **男** [おのこ] — boy; man
- 2742870 **男** [お] — brave; greater; heroic; husband; larger (of the two); male; man; manly
- 2742890 **男** [なん] — son
- 2843485 **男** [だん] — baron; male; man; masculine noun; son

**相**

- 1375250 **相** [さが] — convention; custom; habit; one's destiny; one's nature; tradition
- 1400800 **相** [あい] — adds a nuance of solemnity or formality; equally; mutually; reciprocally; together
- 1400810 **相** [そう] — appearance; aspect; look; phase (e.g. solid, liquid and gaseous); physiognomy (as an indication of one's fortune)
- 1593420 **相** [こもごも] — alternately; in succession
- 2243120 **相** [しょう] — minister (of a government department)

**端**

- 1581610 **端** [はし, はじ, はな] — beginning; edge; end (e.g. of street); first; least; margin; odd bit; odds and ends; point; scrap; start; tip
- 2590770 **端** [はした] — fraction; low class female servant; odd money; odd sum; small change
- 2746070 **端** [つま] — edge; end; gable; gable wall; tip
- 2764560 **端** [たん] — beginning; extremity; for haori: at least 7.27 m in length; for kimonos: at least 10 m in length; for other clothes: at least 6.06 m in length; origin; tip; variable measure of fabric (28.8 cm in width)
- 2867582 **端** [はた] — bystanders; onlookers; others; outsiders; third person

### 20 случаев с наибольшим количеством повторов gloss rows

| Written | Raw rows | Unique glosses | Duplicated rows |
| --- | --- | --- | --- |
| 可愛い | 142 | 18 | 124 |
| 酷い | 127 | 30 | 97 |
| 捷い | 84 | 18 | 66 |
| 上る | 125 | 64 | 61 |
| 間 | 100 | 47 | 53 |
| 私語 | 59 | 10 | 49 |
| 私 | 54 | 11 | 43 |
| 種 | 76 | 38 | 38 |
| 狭間 | 52 | 16 | 36 |
| 山 | 66 | 31 | 35 |
| 臭い | 60 | 28 | 32 |
| 決る | 62 | 30 | 32 |
| 彼方此方 | 42 | 10 | 32 |
| 彼処 | 43 | 11 | 32 |
| 大丈夫 | 49 | 17 | 32 |
| 所 | 62 | 31 | 31 |
| 鈍い | 48 | 19 | 29 |
| 端 | 59 | 31 | 28 |
| 縁 | 65 | 38 | 27 |
| 物 | 63 | 36 | 27 |

Полные readings/meanings каждого entry этих 20 случаев находятся также в `report.json → examples.duplicate_gloss_top`. Многие значения реально различаются: 前 — направления, прошлое, порции, criminal record; 日 — sun/day, Japan/Sunday, day counters. CI identity/overlap не измеряет смысловую тождественность.

## 4. Multi-written JMdict entries

Distribution imported distinct written per entry:
| Значение | Количество |
| --- | --- |
| 1 | 139 866 |
| 2 | 24 086 |
| 3 | 7 076 |
| 4 | 2 877 |
| 5 | 919 |
| 6 | 484 |
| 7 | 182 |
| 8 | 70 |
| 9 | 45 |
| 10 | 29 |
| 11 | 12 |
| 12 | 14 |
| 13 | 5 |
| 14 | 3 |
| 15 | 1 |
| 17 | 1 |

- Только 1: 139 866 entries, 79.62%.
- 2+: 35 804 entries, 20.38%.
- 3+: 11 718 entries, 6.67%.
- 5+: 1 765 entries, 1.00%.

Maximum imported forms: 17. Raw source distribution (0 means no k_ele, not a zero-form imported word):

| Значение | Количество |
| --- | --- |
| 0 | 41 236 |
| 1 | 141 558 |
| 2 | 24 191 |
| 3 | 7 109 |
| 4 | 2 905 |
| 5 | 928 |
| 6 | 495 |
| 7 | 181 |
| 8 | 72 |
| 9 | 45 |
| 10 | 29 |
| 11 | 12 |
| 12 | 14 |
| 13 | 5 |
| 14 | 3 |
| 15 | 1 |
| 17 | 1 |

- Source written-bearing entries only 1: 141 558, 79.73%.
- Source written-bearing entries 2+: 35 991, 20.27%.
- Source written-bearing entries 3+: 11 800, 6.65%.
- Source written-bearing entries 5+: 1 786, 1.01%.

### Запрошенные variants

- 1598870 **飛切り** [とびきり] — best; by far; exceptionally; extra-fine; extraordinarily; jumping and slashing (e.g. at an enemy); superfine; unequalled
- 1598870 **飛び切り** [とびきり] — best; by far; exceptionally; extra-fine; extraordinarily; jumping and slashing (e.g. at an enemy); superfine; unequalled
- 1598870 **飛びきり** [とびきり] — best; by far; exceptionally; extra-fine; extraordinarily; jumping and slashing (e.g. at an enemy); superfine; unequalled
- 2833447 **とびっ切り** [とびっきり] — best; by far; exceptionally; extra-fine; extraordinarily; superfine; unequalled
- 1289180 **今時** [いまどき, イマドキ] — at this hour; at this time (of day); modern times; nowadays; present age; these days; today
- 2849332 **今時** [こんじ] — currently; now; nowadays; present day; presently; these days
- 1289180 **今どき** [いまどき, イマドキ] — at this hour; at this time (of day); modern times; nowadays; present age; these days; today
- 1761170 **学びや** [まなびや] — school; school building; schoolhouse
- 1761170 **学び舎** [まなびや] — school; school building; schoolhouse
- 1206790 **学舎** [がくしゃ] — school (building)
- 1761170 **学舎** [まなびや] — school; school building; schoolhouse

飛切り/飛び切り/飛びきり — entry 1598870; とびっ切り — отдельный 2833447, не reading variation внутри того же entry. 今時/今どき — 1289180, но 今時 ещё есть в 2849332. 学びや/学び舎/学舎 — 1761170, и 学舎 отдельно в 1206790.

Полные source written sets запрошенных lexical entries (включая формы вне named sample):

- 1206790: 学舎; readings: がくしゃ
- 1289180: 今時 / 今どき; readings: いまどき, イマドキ
- 1598870: 飛び切り / 飛びきり / 飛切り; readings: とびきり
- 1761170: 学び舎 / 学舎 / 庠 / 学びや; readings: まなびや
- 2833447: 飛びっきり / 飛びっ切り / とびっ切り; readings: とびっきり
- 2849332: 今時; readings: こんじ

### 35 representative multi-written entries (extreme forms count; обязательные обычные примеры выше)

- Entry **1872660**: 俱利伽羅紋々 / 俱利伽羅紋紋 / 俱利迦羅紋々 / 俱利迦羅紋紋 / 俱梨伽羅紋々 / 俱梨伽羅紋紋 / 俱梨迦羅紋々 / 俱梨迦羅紋紋 / 倶利伽羅もんもん / 倶利伽羅紋々 / 倶利伽羅紋紋 / 倶利迦羅紋々 / 倶利迦羅紋紋 / 倶梨伽羅紋々 / 倶梨伽羅紋紋 / 倶梨迦羅紋々 / 倶梨迦羅紋紋
  - 1872660 **俱利伽羅紋々** [くりからもんもん] — person with a tattoo; tattoo (esp. one of a flame-wreathed dragon wrapped around and swallowing the tip of an upright sword)
  - 1872660 **俱利伽羅紋紋** [くりからもんもん] — person with a tattoo; tattoo (esp. one of a flame-wreathed dragon wrapped around and swallowing the tip of an upright sword)
  - 1872660 **俱利迦羅紋々** [くりからもんもん] — person with a tattoo; tattoo (esp. one of a flame-wreathed dragon wrapped around and swallowing the tip of an upright sword)
  - 1872660 **俱利迦羅紋紋** [くりからもんもん] — person with a tattoo; tattoo (esp. one of a flame-wreathed dragon wrapped around and swallowing the tip of an upright sword)
  - 1872660 **俱梨伽羅紋々** [くりからもんもん] — person with a tattoo; tattoo (esp. one of a flame-wreathed dragon wrapped around and swallowing the tip of an upright sword)
  - 1872660 **俱梨伽羅紋紋** [くりからもんもん] — person with a tattoo; tattoo (esp. one of a flame-wreathed dragon wrapped around and swallowing the tip of an upright sword)
  - 1872660 **俱梨迦羅紋々** [くりからもんもん] — person with a tattoo; tattoo (esp. one of a flame-wreathed dragon wrapped around and swallowing the tip of an upright sword)
  - 1872660 **俱梨迦羅紋紋** [くりからもんもん] — person with a tattoo; tattoo (esp. one of a flame-wreathed dragon wrapped around and swallowing the tip of an upright sword)
  - 1872660 **倶利伽羅もんもん** [くりからもんもん] — person with a tattoo; tattoo (esp. one of a flame-wreathed dragon wrapped around and swallowing the tip of an upright sword)
  - 1872660 **倶利伽羅紋々** [くりからもんもん] — person with a tattoo; tattoo (esp. one of a flame-wreathed dragon wrapped around and swallowing the tip of an upright sword)
  - 1872660 **倶利伽羅紋紋** [くりからもんもん] — person with a tattoo; tattoo (esp. one of a flame-wreathed dragon wrapped around and swallowing the tip of an upright sword)
  - 1872660 **倶利迦羅紋々** [くりからもんもん] — person with a tattoo; tattoo (esp. one of a flame-wreathed dragon wrapped around and swallowing the tip of an upright sword)
  - 1872660 **倶利迦羅紋紋** [くりからもんもん] — person with a tattoo; tattoo (esp. one of a flame-wreathed dragon wrapped around and swallowing the tip of an upright sword)
  - 1872660 **倶梨伽羅紋々** [くりからもんもん] — person with a tattoo; tattoo (esp. one of a flame-wreathed dragon wrapped around and swallowing the tip of an upright sword)
  - 1872660 **倶梨伽羅紋紋** [くりからもんもん] — person with a tattoo; tattoo (esp. one of a flame-wreathed dragon wrapped around and swallowing the tip of an upright sword)
  - 1872660 **倶梨迦羅紋々** [くりからもんもん] — person with a tattoo; tattoo (esp. one of a flame-wreathed dragon wrapped around and swallowing the tip of an upright sword)
  - 1872660 **倶梨迦羅紋紋** [くりからもんもん] — person with a tattoo; tattoo (esp. one of a flame-wreathed dragon wrapped around and swallowing the tip of an upright sword)

- Entry **2119840**: あることない事 / あること無いこと / あること無い事 / ある事ないこと / ある事ない事 / ある事無いこと / ある事無い事 / 在ること無いこと / 在る事無い事 / 有ることないこと / 有ること無いこと / 有ること無い事 / 有る事ない事 / 有る事無いこと / 有る事無い事
  - 2119840 **あることない事** [あることないこと] — half-truth; mixture of fact and fiction
  - 2119840 **あること無いこと** [あることないこと] — half-truth; mixture of fact and fiction
  - 2119840 **あること無い事** [あることないこと] — half-truth; mixture of fact and fiction
  - 2119840 **ある事ないこと** [あることないこと] — half-truth; mixture of fact and fiction
  - 2119840 **ある事ない事** [あることないこと] — half-truth; mixture of fact and fiction
  - 2119840 **ある事無いこと** [あることないこと] — half-truth; mixture of fact and fiction
  - 2119840 **ある事無い事** [あることないこと] — half-truth; mixture of fact and fiction
  - 2119840 **在ること無いこと** [あることないこと] — half-truth; mixture of fact and fiction
  - 2119840 **在る事無い事** [あることないこと] — half-truth; mixture of fact and fiction
  - 2119840 **有ることないこと** [あることないこと] — half-truth; mixture of fact and fiction
  - 2119840 **有ること無いこと** [あることないこと] — half-truth; mixture of fact and fiction
  - 2119840 **有ること無い事** [あることないこと] — half-truth; mixture of fact and fiction
  - 2119840 **有る事ない事** [あることないこと] — half-truth; mixture of fact and fiction
  - 2119840 **有る事無いこと** [あることないこと] — half-truth; mixture of fact and fiction
  - 2119840 **有る事無い事** [あることないこと] — half-truth; mixture of fact and fiction

- Entry **1482900**: いやという程 / いやと言うほど / いやと言う程 / イヤという程 / イヤと言うほど / イヤと言う程 / 厭というほど / 厭と言うほど / 否というほど / 否と言うほど / 嫌というほど / 嫌という程 / 嫌と言うほど / 嫌と言う程
  - 1482900 **いやという程** [いやというほど, イヤというほど] — (more than) enough; bitterly; extremely; keenly; too much
  - 1482900 **いやと言うほど** [いやというほど, イヤというほど] — (more than) enough; bitterly; extremely; keenly; too much
  - 1482900 **いやと言う程** [いやというほど, イヤというほど] — (more than) enough; bitterly; extremely; keenly; too much
  - 1482900 **イヤという程** [いやというほど, イヤというほど] — (more than) enough; bitterly; extremely; keenly; too much
  - 1482900 **イヤと言うほど** [いやというほど, イヤというほど] — (more than) enough; bitterly; extremely; keenly; too much
  - 1482900 **イヤと言う程** [いやというほど, イヤというほど] — (more than) enough; bitterly; extremely; keenly; too much
  - 1482900 **厭というほど** [いやというほど, イヤというほど] — (more than) enough; bitterly; extremely; keenly; too much
  - 1482900 **厭と言うほど** [いやというほど, イヤというほど] — (more than) enough; bitterly; extremely; keenly; too much
  - 1482900 **否というほど** [いやというほど, イヤというほど] — (more than) enough; bitterly; extremely; keenly; too much
  - 1482900 **否と言うほど** [いやというほど, イヤというほど] — (more than) enough; bitterly; extremely; keenly; too much
  - 1482900 **嫌というほど** [いやというほど, イヤというほど] — (more than) enough; bitterly; extremely; keenly; too much
  - 1482900 **嫌という程** [いやというほど, イヤというほど] — (more than) enough; bitterly; extremely; keenly; too much
  - 1482900 **嫌と言うほど** [いやというほど, イヤというほど] — (more than) enough; bitterly; extremely; keenly; too much
  - 1482900 **嫌と言う程** [いやというほど, イヤというほど] — (more than) enough; bitterly; extremely; keenly; too much

- Entry **1587420**: 一か所 / 一カ所 / 一ケ所 / 一ヵ所 / 一ヶ所 / 一個所 / 一箇所 / １か所 / １カ所 / １ケ所 / １ヵ所 / １ヶ所 / １個所 / １箇所
  - 1587420 **一か所** [いっかしょ] — (all in) the same place; one location; one part; one passage; one place; one spot
  - 1587420 **一カ所** [いっかしょ] — (all in) the same place; one location; one part; one passage; one place; one spot
  - 1587420 **一ケ所** [いっかしょ] — (all in) the same place; one location; one part; one passage; one place; one spot
  - 1587420 **一ヵ所** [いっかしょ] — (all in) the same place; one location; one part; one passage; one place; one spot
  - 1587420 **一ヶ所** [いっかしょ] — (all in) the same place; one location; one part; one passage; one place; one spot
  - 1587420 **一個所** [いっかしょ] — (all in) the same place; one location; one part; one passage; one place; one spot
  - 1587420 **一箇所** [いっかしょ] — (all in) the same place; one location; one part; one passage; one place; one spot
  - 1587420 **１か所** [いっかしょ] — (all in) the same place; one location; one part; one passage; one place; one spot
  - 1587420 **１カ所** [いっかしょ] — (all in) the same place; one location; one part; one passage; one place; one spot
  - 1587420 **１ケ所** [いっかしょ] — (all in) the same place; one location; one part; one passage; one place; one spot
  - 1587420 **１ヵ所** [いっかしょ] — (all in) the same place; one location; one part; one passage; one place; one spot
  - 1587420 **１ヶ所** [いっかしょ] — (all in) the same place; one location; one part; one passage; one place; one spot
  - 1587420 **１個所** [いっかしょ] — (all in) the same place; one location; one part; one passage; one place; one spot
  - 1587420 **１箇所** [いっかしょ] — (all in) the same place; one location; one part; one passage; one place; one spot

- Entry **2869699**: けんかするほど仲がいい / けんかするほど仲がよい / けんかするほど仲が良い / ケンカするほど仲がいい / ケンカするほど仲がよい / ケンカするほど仲が良い / ケンカする程仲がいい / ケンカする程仲が良い / 喧嘩するほどなかがいい / 喧嘩するほど仲がいい / 喧嘩するほど仲がよい / 喧嘩するほど仲が良い / 喧嘩する程仲がいい / 喧嘩する程仲が良い
  - 2869699 **けんかするほど仲がいい** [けんかするほどなかがいい] — the more (people) fight, the closer they are; those who quarrel most are often the best of friends
  - 2869699 **けんかするほど仲がよい** [けんかするほどなかがいい] — the more (people) fight, the closer they are; those who quarrel most are often the best of friends
  - 2869699 **けんかするほど仲が良い** [けんかするほどなかがいい] — the more (people) fight, the closer they are; those who quarrel most are often the best of friends
  - 2869699 **ケンカするほど仲がいい** [けんかするほどなかがいい] — the more (people) fight, the closer they are; those who quarrel most are often the best of friends
  - 2869699 **ケンカするほど仲がよい** [けんかするほどなかがいい] — the more (people) fight, the closer they are; those who quarrel most are often the best of friends
  - 2869699 **ケンカするほど仲が良い** [けんかするほどなかがいい] — the more (people) fight, the closer they are; those who quarrel most are often the best of friends
  - 2869699 **ケンカする程仲がいい** [けんかするほどなかがいい] — the more (people) fight, the closer they are; those who quarrel most are often the best of friends
  - 2869699 **ケンカする程仲が良い** [けんかするほどなかがいい] — the more (people) fight, the closer they are; those who quarrel most are often the best of friends
  - 2869699 **喧嘩するほどなかがいい** [けんかするほどなかがいい] — the more (people) fight, the closer they are; those who quarrel most are often the best of friends
  - 2869699 **喧嘩するほど仲がいい** [けんかするほどなかがいい] — the more (people) fight, the closer they are; those who quarrel most are often the best of friends
  - 2869699 **喧嘩するほど仲がよい** [けんかするほどなかがいい] — the more (people) fight, the closer they are; those who quarrel most are often the best of friends
  - 2869699 **喧嘩するほど仲が良い** [けんかするほどなかがいい] — the more (people) fight, the closer they are; those who quarrel most are often the best of friends
  - 2869699 **喧嘩する程仲がいい** [けんかするほどなかがいい] — the more (people) fight, the closer they are; those who quarrel most are often the best of friends
  - 2869699 **喧嘩する程仲が良い** [けんかするほどなかがいい] — the more (people) fight, the closer they are; those who quarrel most are often the best of friends

- Entry **1595900**: すり硝子 / 摺りガラス / 摺り硝子 / 摺ガラス / 摺硝子 / 擦りガラス / 擦り硝子 / 擦ガラス / 擦硝子 / 磨りガラス / 磨り硝子 / 磨ガラス / 磨硝子
  - 1595900 **すり硝子** [すりガラス] — frosted glass; ground glass
  - 1595900 **摺りガラス** [すりガラス] — frosted glass; ground glass
  - 1595900 **摺り硝子** [すりガラス] — frosted glass; ground glass
  - 1595900 **摺ガラス** [すりガラス] — frosted glass; ground glass
  - 1595900 **摺硝子** [すりガラス] — frosted glass; ground glass
  - 1595900 **擦りガラス** [すりガラス] — frosted glass; ground glass
  - 1595900 **擦り硝子** [すりガラス] — frosted glass; ground glass
  - 1595900 **擦ガラス** [すりガラス] — frosted glass; ground glass
  - 1595900 **擦硝子** [すりガラス] — frosted glass; ground glass
  - 1595900 **磨りガラス** [すりガラス] — frosted glass; ground glass
  - 1595900 **磨り硝子** [すりガラス] — frosted glass; ground glass
  - 1595900 **磨ガラス** [すりガラス] — frosted glass; ground glass
  - 1595900 **磨硝子** [すりガラス] — frosted glass; ground glass

- Entry **1838050**: 立ち居ふるまい / 立ち居振るまい / 立ち居振る舞 / 立ち居振る舞い / 立ち居振舞 / 立ち居振舞い / 立居ふるまい / 立居振る舞い / 立居振舞 / 立居振舞い / 起ち居振る舞い / 起ち居振舞い / 起居振舞
  - 1838050 **立ち居ふるまい** [たちいふるまい] — bearing; behavior; behaviour; carriage; demeanor; demeanour; deportment; manners; movements
  - 1838050 **立ち居振るまい** [たちいふるまい] — bearing; behavior; behaviour; carriage; demeanor; demeanour; deportment; manners; movements
  - 1838050 **立ち居振る舞** [たちいふるまい] — bearing; behavior; behaviour; carriage; demeanor; demeanour; deportment; manners; movements
  - 1838050 **立ち居振る舞い** [たちいふるまい] — bearing; behavior; behaviour; carriage; demeanor; demeanour; deportment; manners; movements
  - 1838050 **立ち居振舞** [たちいふるまい] — bearing; behavior; behaviour; carriage; demeanor; demeanour; deportment; manners; movements
  - 1838050 **立ち居振舞い** [たちいふるまい] — bearing; behavior; behaviour; carriage; demeanor; demeanour; deportment; manners; movements
  - 1838050 **立居ふるまい** [たちいふるまい] — bearing; behavior; behaviour; carriage; demeanor; demeanour; deportment; manners; movements
  - 1838050 **立居振る舞い** [たちいふるまい] — bearing; behavior; behaviour; carriage; demeanor; demeanour; deportment; manners; movements
  - 1838050 **立居振舞** [たちいふるまい] — bearing; behavior; behaviour; carriage; demeanor; demeanour; deportment; manners; movements
  - 1838050 **立居振舞い** [たちいふるまい] — bearing; behavior; behaviour; carriage; demeanor; demeanour; deportment; manners; movements
  - 1838050 **起ち居振る舞い** [たちいふるまい] — bearing; behavior; behaviour; carriage; demeanor; demeanour; deportment; manners; movements
  - 1838050 **起ち居振舞い** [たちいふるまい] — bearing; behavior; behaviour; carriage; demeanor; demeanour; deportment; manners; movements
  - 1838050 **起居振舞** [たちいふるまい] — bearing; behavior; behaviour; carriage; demeanor; demeanour; deportment; manners; movements

- Entry **1915350**: らちが明かない / らちが明かぬ / らちが明かん / ラチが明かない / ラチが明かぬ / ラチが明かん / ラチが開かない / 埒があかない / 埒があかぬ / 埒があかん / 埒が明かない / 埒が明かぬ / 埒が明かん
  - 1915350 **らちが明かない** [らちがあかない, らちがあかぬ, らちがあかん, ラチがあかない, ラチがあかぬ, ラチがあかん] — getting nowhere; making no progress; remaining unsettled
  - 1915350 **らちが明かぬ** [らちがあかない, らちがあかぬ, らちがあかん, ラチがあかない, ラチがあかぬ, ラチがあかん] — getting nowhere; making no progress; remaining unsettled
  - 1915350 **らちが明かん** [らちがあかない, らちがあかぬ, らちがあかん, ラチがあかない, ラチがあかぬ, ラチがあかん] — getting nowhere; making no progress; remaining unsettled
  - 1915350 **ラチが明かない** [らちがあかない, らちがあかぬ, らちがあかん, ラチがあかない, ラチがあかぬ, ラチがあかん] — getting nowhere; making no progress; remaining unsettled
  - 1915350 **ラチが明かぬ** [らちがあかない, らちがあかぬ, らちがあかん, ラチがあかない, ラチがあかぬ, ラチがあかん] — getting nowhere; making no progress; remaining unsettled
  - 1915350 **ラチが明かん** [らちがあかない, らちがあかぬ, らちがあかん, ラチがあかない, ラチがあかぬ, ラチがあかん] — getting nowhere; making no progress; remaining unsettled
  - 1915350 **ラチが開かない** [らちがあかない, らちがあかぬ, らちがあかん, ラチがあかない, ラチがあかぬ, ラチがあかん] — getting nowhere; making no progress; remaining unsettled
  - 1915350 **埒があかない** [らちがあかない, らちがあかぬ, らちがあかん, ラチがあかない, ラチがあかぬ, ラチがあかん] — getting nowhere; making no progress; remaining unsettled
  - 1915350 **埒があかぬ** [らちがあかない, らちがあかぬ, らちがあかん, ラチがあかない, ラチがあかぬ, ラチがあかん] — getting nowhere; making no progress; remaining unsettled
  - 1915350 **埒があかん** [らちがあかない, らちがあかぬ, らちがあかん, ラチがあかない, ラチがあかぬ, ラチがあかん] — getting nowhere; making no progress; remaining unsettled
  - 1915350 **埒が明かない** [らちがあかない, らちがあかぬ, らちがあかん, ラチがあかない, ラチがあかぬ, ラチがあかん] — getting nowhere; making no progress; remaining unsettled
  - 1915350 **埒が明かぬ** [らちがあかない, らちがあかぬ, らちがあかん, ラチがあかない, ラチがあかぬ, ラチがあかん] — getting nowhere; making no progress; remaining unsettled
  - 1915350 **埒が明かん** [らちがあかない, らちがあかぬ, らちがあかん, ラチがあかない, ラチがあかぬ, ラチがあかん] — getting nowhere; making no progress; remaining unsettled

- Entry **2038420**: わかり合う / 分かりあう / 分かり合う / 分りあう / 分り合う / 判りあう / 判り合う / 理解りあう / 理解り合う / 解かりあう / 解かり合う / 解りあう / 解り合う
  - 2038420 **わかり合う** [わかりあう] — to understand each other
  - 2038420 **分かりあう** [わかりあう] — to understand each other
  - 2038420 **分かり合う** [わかりあう] — to understand each other
  - 2038420 **分りあう** [わかりあう] — to understand each other
  - 2038420 **分り合う** [わかりあう] — to understand each other
  - 2038420 **判りあう** [わかりあう] — to understand each other
  - 2038420 **判り合う** [わかりあう] — to understand each other
  - 2038420 **理解りあう** [わかりあう] — to understand each other
  - 2038420 **理解り合う** [わかりあう] — to understand each other
  - 2038420 **解かりあう** [わかりあう] — to understand each other
  - 2038420 **解かり合う** [わかりあう] — to understand each other
  - 2038420 **解りあう** [わかりあう] — to understand each other
  - 2038420 **解り合う** [わかりあう] — to understand each other

- Entry **2838728**: げすの勘ぐり / げすの勘繰り / ゲスの勘ぐり / ゲスの勘繰り / 下司のかんぐり / 下司の勘ぐり / 下司の勘繰り / 下種のかんぐり / 下種の勘ぐり / 下種の勘繰り / 下衆のかんぐり / 下衆の勘ぐり / 下衆の勘繰り
  - 2838728 **げすの勘ぐり** [げすのかんぐり] — petty-minded suspicion; putting a deliberately bad interpretation (on a person's actions)
  - 2838728 **げすの勘繰り** [げすのかんぐり] — petty-minded suspicion; putting a deliberately bad interpretation (on a person's actions)
  - 2838728 **ゲスの勘ぐり** [げすのかんぐり] — petty-minded suspicion; putting a deliberately bad interpretation (on a person's actions)
  - 2838728 **ゲスの勘繰り** [げすのかんぐり] — petty-minded suspicion; putting a deliberately bad interpretation (on a person's actions)
  - 2838728 **下司のかんぐり** [げすのかんぐり] — petty-minded suspicion; putting a deliberately bad interpretation (on a person's actions)
  - 2838728 **下司の勘ぐり** [げすのかんぐり] — petty-minded suspicion; putting a deliberately bad interpretation (on a person's actions)
  - 2838728 **下司の勘繰り** [げすのかんぐり] — petty-minded suspicion; putting a deliberately bad interpretation (on a person's actions)
  - 2838728 **下種のかんぐり** [げすのかんぐり] — petty-minded suspicion; putting a deliberately bad interpretation (on a person's actions)
  - 2838728 **下種の勘ぐり** [げすのかんぐり] — petty-minded suspicion; putting a deliberately bad interpretation (on a person's actions)
  - 2838728 **下種の勘繰り** [げすのかんぐり] — petty-minded suspicion; putting a deliberately bad interpretation (on a person's actions)
  - 2838728 **下衆のかんぐり** [げすのかんぐり] — petty-minded suspicion; putting a deliberately bad interpretation (on a person's actions)
  - 2838728 **下衆の勘ぐり** [げすのかんぐり] — petty-minded suspicion; putting a deliberately bad interpretation (on a person's actions)
  - 2838728 **下衆の勘繰り** [げすのかんぐり] — petty-minded suspicion; putting a deliberately bad interpretation (on a person's actions)

- Entry **1333970**: 住みか / 住み処 / 住み家 / 住処 / 住家 / 栖 / 栖家 / 棲 / 棲み処 / 棲み家 / 棲処 / 棲家
  - 1333970 **住みか** [すみか] — abode; den (of criminals, demons, etc.); dwelling; habitat; home; house; nest; residence
  - 1333970 **住み処** [すみか] — abode; den (of criminals, demons, etc.); dwelling; habitat; home; house; nest; residence
  - 1333970 **住み家** [すみか] — abode; den (of criminals, demons, etc.); dwelling; habitat; home; house; nest; residence
  - 1333970 **住処** [すみか] — abode; den (of criminals, demons, etc.); dwelling; habitat; home; house; nest; residence
  - 1333970 **住家** [すみか] — abode; den (of criminals, demons, etc.); dwelling; habitat; home; house; nest; residence
  - 1333970 **栖** [すみか] — abode; den (of criminals, demons, etc.); dwelling; habitat; home; house; nest; residence
  - 1333970 **栖家** [すみか] — abode; den (of criminals, demons, etc.); dwelling; habitat; home; house; nest; residence
  - 1333970 **棲** [すみか] — abode; den (of criminals, demons, etc.); dwelling; habitat; home; house; nest; residence
  - 1333970 **棲み処** [すみか] — abode; den (of criminals, demons, etc.); dwelling; habitat; home; house; nest; residence
  - 1333970 **棲み家** [すみか] — abode; den (of criminals, demons, etc.); dwelling; habitat; home; house; nest; residence
  - 1333970 **棲処** [すみか] — abode; den (of criminals, demons, etc.); dwelling; habitat; home; house; nest; residence
  - 1333970 **棲家** [すみか] — abode; den (of criminals, demons, etc.); dwelling; habitat; home; house; nest; residence

- Entry **1444570**: 不如帰 / 子規 / 時鳥 / 杜宇 / 杜鵑 / 沓手鳥 / 沓直鳥 / 田鵑 / 蜀魂 / 蜀魄 / 郭公 / 霍公鳥
  - 1444570 **不如帰** [ほととぎす] — lesser cuckoo (Cuculus poliocephalus)
  - 1444570 **子規** [ほととぎす] — lesser cuckoo (Cuculus poliocephalus)
  - 1444570 **時鳥** [ほととぎす] — lesser cuckoo (Cuculus poliocephalus)
  - 1444570 **杜宇** [ほととぎす] — lesser cuckoo (Cuculus poliocephalus)
  - 1444570 **杜鵑** [ほととぎす] — lesser cuckoo (Cuculus poliocephalus)
  - 1444570 **沓手鳥** [ほととぎす] — lesser cuckoo (Cuculus poliocephalus)
  - 1444570 **沓直鳥** [ほととぎす] — lesser cuckoo (Cuculus poliocephalus)
  - 1444570 **田鵑** [ほととぎす] — lesser cuckoo (Cuculus poliocephalus)
  - 1444570 **蜀魂** [ほととぎす] — lesser cuckoo (Cuculus poliocephalus)
  - 1444570 **蜀魄** [ほととぎす] — lesser cuckoo (Cuculus poliocephalus)
  - 1444570 **郭公** [ほととぎす] — lesser cuckoo (Cuculus poliocephalus)
  - 1444570 **霍公鳥** [ほととぎす] — lesser cuckoo (Cuculus poliocephalus)

- Entry **1587410**: 一か月 / 一カ月 / 一ケ月 / 一ヵ月 / 一ヶ月 / 一箇月 / １か月 / １カ月 / １ケ月 / １ヵ月 / １ヶ月 / １箇月
  - 1587410 **一か月** [いっかげつ] — one month
  - 1587410 **一カ月** [いっかげつ] — one month
  - 1587410 **一ケ月** [いっかげつ] — one month
  - 1587410 **一ヵ月** [いっかげつ] — one month
  - 1587410 **一ヶ月** [いっかげつ] — one month
  - 1587410 **一箇月** [いっかげつ] — one month
  - 1587410 **１か月** [いっかげつ] — one month
  - 1587410 **１カ月** [いっかげつ] — one month
  - 1587410 **１ケ月** [いっかげつ] — one month
  - 1587410 **１ヵ月** [いっかげつ] — one month
  - 1587410 **１ヶ月** [いっかげつ] — one month
  - 1587410 **１箇月** [いっかげつ] — one month

- Entry **1590130**: かけ引き / 懸け引き / 掛け引き / 掛引 / 駆けひき / 駆け引 / 駆け引き / 駆引 / 駆引き / 駈け引き / 駈引 / 駈引き
  - 1590130 **かけ引き** [かけひき] — (tactical) advance or retreat of troops; bargaining; diplomacy; haggling; maneuvering; strategy; tactics
  - 1590130 **懸け引き** [かけひき] — (tactical) advance or retreat of troops; bargaining; diplomacy; haggling; maneuvering; strategy; tactics
  - 1590130 **掛け引き** [かけひき] — (tactical) advance or retreat of troops; bargaining; diplomacy; haggling; maneuvering; strategy; tactics
  - 1590130 **掛引** [かけひき] — (tactical) advance or retreat of troops; bargaining; diplomacy; haggling; maneuvering; strategy; tactics
  - 1590130 **駆けひき** [かけひき] — (tactical) advance or retreat of troops; bargaining; diplomacy; haggling; maneuvering; strategy; tactics
  - 1590130 **駆け引** [かけひき] — (tactical) advance or retreat of troops; bargaining; diplomacy; haggling; maneuvering; strategy; tactics
  - 1590130 **駆け引き** [かけひき] — (tactical) advance or retreat of troops; bargaining; diplomacy; haggling; maneuvering; strategy; tactics
  - 1590130 **駆引** [かけひき] — (tactical) advance or retreat of troops; bargaining; diplomacy; haggling; maneuvering; strategy; tactics
  - 1590130 **駆引き** [かけひき] — (tactical) advance or retreat of troops; bargaining; diplomacy; haggling; maneuvering; strategy; tactics
  - 1590130 **駈け引き** [かけひき] — (tactical) advance or retreat of troops; bargaining; diplomacy; haggling; maneuvering; strategy; tactics
  - 1590130 **駈引** [かけひき] — (tactical) advance or retreat of troops; bargaining; diplomacy; haggling; maneuvering; strategy; tactics
  - 1590130 **駈引き** [かけひき] — (tactical) advance or retreat of troops; bargaining; diplomacy; haggling; maneuvering; strategy; tactics

- Entry **1603480**: 乾しぶどう / 乾しブドウ / 乾し葡萄 / 乾ぶどう / 乾ブドウ / 乾葡萄 / 干しぶどう / 干しブドウ / 干し葡萄 / 干ぶどう / 干ブドウ / 干葡萄
  - 1603480 **乾しぶどう** [ほしぶどう] — currants; dried grapes; raisins
  - 1603480 **乾しブドウ** [ほしぶどう] — currants; dried grapes; raisins
  - 1603480 **乾し葡萄** [ほしぶどう] — currants; dried grapes; raisins
  - 1603480 **乾ぶどう** [ほしぶどう] — currants; dried grapes; raisins
  - 1603480 **乾ブドウ** [ほしぶどう] — currants; dried grapes; raisins
  - 1603480 **乾葡萄** [ほしぶどう] — currants; dried grapes; raisins
  - 1603480 **干しぶどう** [ほしぶどう] — currants; dried grapes; raisins
  - 1603480 **干しブドウ** [ほしぶどう] — currants; dried grapes; raisins
  - 1603480 **干し葡萄** [ほしぶどう] — currants; dried grapes; raisins
  - 1603480 **干ぶどう** [ほしぶどう] — currants; dried grapes; raisins
  - 1603480 **干ブドウ** [ほしぶどう] — currants; dried grapes; raisins
  - 1603480 **干葡萄** [ほしぶどう] — currants; dried grapes; raisins

- Entry **2537400**: 二か年 / 二カ年 / 二ケ年 / 二ヵ年 / 二ヶ年 / 二箇年 / ２か年 / ２カ年 / ２ケ年 / ２ヵ年 / ２ヶ年 / ２箇年
  - 2537400 **二か年** [にかねん] — biennial
  - 2537400 **二カ年** [にかねん] — biennial
  - 2537400 **二ケ年** [にかねん] — biennial
  - 2537400 **二ヵ年** [にかねん] — biennial
  - 2537400 **二ヶ年** [にかねん] — biennial
  - 2537400 **二箇年** [にかねん] — biennial
  - 2537400 **２か年** [にかねん] — biennial
  - 2537400 **２カ年** [にかねん] — biennial
  - 2537400 **２ケ年** [にかねん] — biennial
  - 2537400 **２ヵ年** [にかねん] — biennial
  - 2537400 **２ヶ年** [にかねん] — biennial
  - 2537400 **２箇年** [にかねん] — biennial

- Entry **2537410**: 三か年 / 三カ年 / 三ケ年 / 三ヵ年 / 三ヶ年 / 三箇年 / ３か年 / ３カ年 / ３ケ年 / ３ヵ年 / ３ヶ年 / ３箇年
  - 2537410 **三か年** [さんかねん] — triennial
  - 2537410 **三カ年** [さんかねん] — triennial
  - 2537410 **三ケ年** [さんかねん] — triennial
  - 2537410 **三ヵ年** [さんかねん] — triennial
  - 2537410 **三ヶ年** [さんかねん] — triennial
  - 2537410 **三箇年** [さんかねん] — triennial
  - 2537410 **３か年** [さんかねん] — triennial
  - 2537410 **３カ年** [さんかねん] — triennial
  - 2537410 **３ケ年** [さんかねん] — triennial
  - 2537410 **３ヵ年** [さんかねん] — triennial
  - 2537410 **３ヶ年** [さんかねん] — triennial
  - 2537410 **３箇年** [さんかねん] — triennial

- Entry **2537420**: 四か年 / 四カ年 / 四ケ年 / 四ヵ年 / 四ヶ年 / 四箇年 / ４か年 / ４カ年 / ４ケ年 / ４ヵ年 / ４ヶ年 / ４箇年
  - 2537420 **四か年** [よんかねん] — lasting four years; quadrennial; recurring every four years
  - 2537420 **四カ年** [よんかねん] — lasting four years; quadrennial; recurring every four years
  - 2537420 **四ケ年** [よんかねん] — lasting four years; quadrennial; recurring every four years
  - 2537420 **四ヵ年** [よんかねん] — lasting four years; quadrennial; recurring every four years
  - 2537420 **四ヶ年** [よんかねん] — lasting four years; quadrennial; recurring every four years
  - 2537420 **四箇年** [よんかねん] — lasting four years; quadrennial; recurring every four years
  - 2537420 **４か年** [よんかねん] — lasting four years; quadrennial; recurring every four years
  - 2537420 **４カ年** [よんかねん] — lasting four years; quadrennial; recurring every four years
  - 2537420 **４ケ年** [よんかねん] — lasting four years; quadrennial; recurring every four years
  - 2537420 **４ヵ年** [よんかねん] — lasting four years; quadrennial; recurring every four years
  - 2537420 **４ヶ年** [よんかねん] — lasting four years; quadrennial; recurring every four years
  - 2537420 **４箇年** [よんかねん] — lasting four years; quadrennial; recurring every four years

- Entry **2537430**: 五か年 / 五カ年 / 五ケ年 / 五ヵ年 / 五ヶ年 / 五箇年 / ５か年 / ５カ年 / ５ケ年 / ５ヵ年 / ５ヶ年 / ５箇年
  - 2537430 **五か年** [ごかねん] — quinquennial; recurring every five years
  - 2537430 **五カ年** [ごかねん] — quinquennial; recurring every five years
  - 2537430 **五ケ年** [ごかねん] — quinquennial; recurring every five years
  - 2537430 **五ヵ年** [ごかねん] — quinquennial; recurring every five years
  - 2537430 **五ヶ年** [ごかねん] — quinquennial; recurring every five years
  - 2537430 **五箇年** [ごかねん] — quinquennial; recurring every five years
  - 2537430 **５か年** [ごかねん] — quinquennial; recurring every five years
  - 2537430 **５カ年** [ごかねん] — quinquennial; recurring every five years
  - 2537430 **５ケ年** [ごかねん] — quinquennial; recurring every five years
  - 2537430 **５ヵ年** [ごかねん] — quinquennial; recurring every five years
  - 2537430 **５ヶ年** [ごかねん] — quinquennial; recurring every five years
  - 2537430 **５箇年** [ごかねん] — quinquennial; recurring every five years

- Entry **2537440**: 六か年 / 六カ年 / 六ケ年 / 六ヵ年 / 六ヶ年 / 六箇年 / ６か年 / ６カ年 / ６ケ年 / ６ヵ年 / ６ヶ年 / ６箇年
  - 2537440 **六か年** [ろっかねん] — recurring every six years; sexennial
  - 2537440 **六カ年** [ろっかねん] — recurring every six years; sexennial
  - 2537440 **六ケ年** [ろっかねん] — recurring every six years; sexennial
  - 2537440 **六ヵ年** [ろっかねん] — recurring every six years; sexennial
  - 2537440 **六ヶ年** [ろっかねん] — recurring every six years; sexennial
  - 2537440 **六箇年** [ろっかねん] — recurring every six years; sexennial
  - 2537440 **６か年** [ろっかねん] — recurring every six years; sexennial
  - 2537440 **６カ年** [ろっかねん] — recurring every six years; sexennial
  - 2537440 **６ケ年** [ろっかねん] — recurring every six years; sexennial
  - 2537440 **６ヵ年** [ろっかねん] — recurring every six years; sexennial
  - 2537440 **６ヶ年** [ろっかねん] — recurring every six years; sexennial
  - 2537440 **６箇年** [ろっかねん] — recurring every six years; sexennial

- Entry **2537450**: 七か年 / 七カ年 / 七ケ年 / 七ヵ年 / 七ヶ年 / 七箇年 / ７か年 / ７カ年 / ７ケ年 / ７ヵ年 / ７ヶ年 / ７箇年
  - 2537450 **七か年** [ななかねん] — recurring every seven years; septennial
  - 2537450 **七カ年** [ななかねん] — recurring every seven years; septennial
  - 2537450 **七ケ年** [ななかねん] — recurring every seven years; septennial
  - 2537450 **七ヵ年** [ななかねん] — recurring every seven years; septennial
  - 2537450 **七ヶ年** [ななかねん] — recurring every seven years; septennial
  - 2537450 **七箇年** [ななかねん] — recurring every seven years; septennial
  - 2537450 **７か年** [ななかねん] — recurring every seven years; septennial
  - 2537450 **７カ年** [ななかねん] — recurring every seven years; septennial
  - 2537450 **７ケ年** [ななかねん] — recurring every seven years; septennial
  - 2537450 **７ヵ年** [ななかねん] — recurring every seven years; septennial
  - 2537450 **７ヶ年** [ななかねん] — recurring every seven years; septennial
  - 2537450 **７箇年** [ななかねん] — recurring every seven years; septennial

- Entry **2537460**: 八か年 / 八カ年 / 八ケ年 / 八ヵ年 / 八ヶ年 / 八箇年 / ８か年 / ８カ年 / ８ケ年 / ８ヵ年 / ８ヶ年 / ８箇年
  - 2537460 **八か年** [はちかねん] — octennial; recurring every eight years
  - 2537460 **八カ年** [はちかねん] — octennial; recurring every eight years
  - 2537460 **八ケ年** [はちかねん] — octennial; recurring every eight years
  - 2537460 **八ヵ年** [はちかねん] — octennial; recurring every eight years
  - 2537460 **八ヶ年** [はちかねん] — octennial; recurring every eight years
  - 2537460 **八箇年** [はちかねん] — octennial; recurring every eight years
  - 2537460 **８か年** [はちかねん] — octennial; recurring every eight years
  - 2537460 **８カ年** [はちかねん] — octennial; recurring every eight years
  - 2537460 **８ケ年** [はちかねん] — octennial; recurring every eight years
  - 2537460 **８ヵ年** [はちかねん] — octennial; recurring every eight years
  - 2537460 **８ヶ年** [はちかねん] — octennial; recurring every eight years
  - 2537460 **８箇年** [はちかねん] — octennial; recurring every eight years

- Entry **2537470**: 九か年 / 九カ年 / 九ケ年 / 九ヵ年 / 九ヶ年 / 九箇年 / ９か年 / ９カ年 / ９ケ年 / ９ヵ年 / ９ヶ年 / ９箇年
  - 2537470 **九か年** [きゅうかねん] — novennial; recurring every nine years
  - 2537470 **九カ年** [きゅうかねん] — novennial; recurring every nine years
  - 2537470 **九ケ年** [きゅうかねん] — novennial; recurring every nine years
  - 2537470 **九ヵ年** [きゅうかねん] — novennial; recurring every nine years
  - 2537470 **九ヶ年** [きゅうかねん] — novennial; recurring every nine years
  - 2537470 **九箇年** [きゅうかねん] — novennial; recurring every nine years
  - 2537470 **９か年** [きゅうかねん] — novennial; recurring every nine years
  - 2537470 **９カ年** [きゅうかねん] — novennial; recurring every nine years
  - 2537470 **９ケ年** [きゅうかねん] — novennial; recurring every nine years
  - 2537470 **９ヵ年** [きゅうかねん] — novennial; recurring every nine years
  - 2537470 **９ヶ年** [きゅうかねん] — novennial; recurring every nine years
  - 2537470 **９箇年** [きゅうかねん] — novennial; recurring every nine years

- Entry **2537480**: 十か年 / 十カ年 / 十ケ年 / 十ヵ年 / 十ヶ年 / 十箇年 / １０か年 / １０カ年 / １０ケ年 / １０ヵ年 / １０ヶ年 / １０箇年
  - 2537480 **十か年** [じゅうかねん] — decennial; recurring every ten years
  - 2537480 **十カ年** [じゅうかねん] — decennial; recurring every ten years
  - 2537480 **十ケ年** [じゅうかねん] — decennial; recurring every ten years
  - 2537480 **十ヵ年** [じゅうかねん] — decennial; recurring every ten years
  - 2537480 **十ヶ年** [じゅうかねん] — decennial; recurring every ten years
  - 2537480 **十箇年** [じゅうかねん] — decennial; recurring every ten years
  - 2537480 **１０か年** [じゅうかねん] — decennial; recurring every ten years
  - 2537480 **１０カ年** [じゅうかねん] — decennial; recurring every ten years
  - 2537480 **１０ケ年** [じゅうかねん] — decennial; recurring every ten years
  - 2537480 **１０ヵ年** [じゅうかねん] — decennial; recurring every ten years
  - 2537480 **１０ヶ年** [じゅうかねん] — decennial; recurring every ten years
  - 2537480 **１０箇年** [じゅうかねん] — decennial; recurring every ten years

- Entry **1297360**: つくり変える / つくり換える / つくり替える / 作りかえる / 作り変える / 作り換える / 作り替える / 造りかえる / 造り変える / 造り換える / 造り替える
  - 1297360 **つくり変える** [つくりかえる] — to adapt; to alter; to convert; to parody; to reconstruct; to remake; to remold; to remould
  - 1297360 **つくり換える** [つくりかえる] — to adapt; to alter; to convert; to parody; to reconstruct; to remake; to remold; to remould
  - 1297360 **つくり替える** [つくりかえる] — to adapt; to alter; to convert; to parody; to reconstruct; to remake; to remold; to remould
  - 1297360 **作りかえる** [つくりかえる] — to adapt; to alter; to convert; to parody; to reconstruct; to remake; to remold; to remould
  - 1297360 **作り変える** [つくりかえる] — to adapt; to alter; to convert; to parody; to reconstruct; to remake; to remold; to remould
  - 1297360 **作り換える** [つくりかえる] — to adapt; to alter; to convert; to parody; to reconstruct; to remake; to remold; to remould
  - 1297360 **作り替える** [つくりかえる] — to adapt; to alter; to convert; to parody; to reconstruct; to remake; to remold; to remould
  - 1297360 **造りかえる** [つくりかえる] — to adapt; to alter; to convert; to parody; to reconstruct; to remake; to remold; to remould
  - 1297360 **造り変える** [つくりかえる] — to adapt; to alter; to convert; to parody; to reconstruct; to remake; to remold; to remould
  - 1297360 **造り換える** [つくりかえる] — to adapt; to alter; to convert; to parody; to reconstruct; to remake; to remold; to remould
  - 1297360 **造り替える** [つくりかえる] — to adapt; to alter; to convert; to parody; to reconstruct; to remake; to remold; to remould

- Entry **1429690**: とび上がる / とび上る / 跳びあがる / 跳び上がる / 跳び上る / 跳上る / 飛びあがる / 飛び上がる / 飛び上る / 飛上がる / 飛上る
  - 1429690 **とび上がる** [とびあがる] — to jump up; to leap up; to spring up
  - 1429690 **とび上る** [とびあがる] — to jump up; to leap up; to spring up
  - 1429690 **跳びあがる** [とびあがる] — to jump up; to leap up; to spring up
  - 1429690 **跳び上がる** [とびあがる] — to jump up; to leap up; to spring up
  - 1429690 **跳び上る** [とびあがる] — to jump up; to leap up; to spring up
  - 1429690 **跳上る** [とびあがる] — to jump up; to leap up; to spring up
  - 1429690 **飛びあがる** [とびあがる] — to jump up; to leap up; to spring up
  - 1429690 **飛び上がる** [とびあがる] — to fly up (into the sky); to jump ahead; to jump up; to leap up; to skip ahead; to soar up; to spring up; to take off
  - 1429690 **飛び上る** [とびあがる] — to jump up; to leap up; to spring up
  - 1429690 **飛上がる** [とびあがる] — to jump up; to leap up; to spring up
  - 1429690 **飛上る** [とびあがる] — to jump up; to leap up; to spring up

- Entry **1501680**: わき上がる / 沸きあがる / 沸き上がる / 沸き上る / 沸上がる / 涌きあがる / 涌き上がる / 湧きあがる / 湧き上がる / 湧き上る / 湧上る
  - 1501680 **わき上がる** [わきあがる] — to arise; to be in uproar; to boil up; to break out; to come to the boil; to get excited; to seethe
  - 1501680 **沸きあがる** [わきあがる] — to arise; to be in uproar; to boil up; to break out; to come to the boil; to get excited; to seethe
  - 1501680 **沸き上がる** [わきあがる] — to arise; to be in uproar; to boil up; to break out; to come to the boil; to get excited; to seethe
  - 1501680 **沸き上る** [わきあがる] — to arise; to be in uproar; to boil up; to break out; to come to the boil; to get excited; to seethe
  - 1501680 **沸上がる** [わきあがる] — to arise; to be in uproar; to boil up; to break out; to come to the boil; to get excited; to seethe
  - 1501680 **涌きあがる** [わきあがる] — to arise; to be in uproar; to boil up; to break out; to come to the boil; to get excited; to seethe
  - 1501680 **涌き上がる** [わきあがる] — to arise; to be in uproar; to boil up; to break out; to come to the boil; to get excited; to seethe
  - 1501680 **湧きあがる** [わきあがる] — to arise; to be in uproar; to boil up; to break out; to come to the boil; to get excited; to seethe
  - 1501680 **湧き上がる** [わきあがる] — to arise; to be in uproar; to boil up; to break out; to come to the boil; to get excited; to seethe
  - 1501680 **湧き上る** [わきあがる] — to arise; to be in uproar; to boil up; to break out; to come to the boil; to get excited; to seethe
  - 1501680 **湧上る** [わきあがる] — to arise; to be in uproar; to boil up; to break out; to come to the boil; to get excited; to seethe

- Entry **1571980**: もぬけの殻 / もぬけの空 / モヌケの殻 / モヌケの空 / 藻抜けの殻 / 蛻けの殻 / 蛻のから / 蛻の殻 / 蛻の空 / 裳抜けの殻 / 裳脱けの殻
  - 1571980 **もぬけの殻** [もぬけのから, もぬけのカラ] — body from which the soul has left; completely empty (of a residence, etc.); corpse; deserted; shed skin (of a snake, insect, etc.); vacant
  - 1571980 **もぬけの空** [もぬけのから, もぬけのカラ] — body from which the soul has left; completely empty (of a residence, etc.); corpse; deserted; shed skin (of a snake, insect, etc.); vacant
  - 1571980 **モヌケの殻** [もぬけのから, もぬけのカラ] — body from which the soul has left; completely empty (of a residence, etc.); corpse; deserted; shed skin (of a snake, insect, etc.); vacant
  - 1571980 **モヌケの空** [もぬけのから, もぬけのカラ] — body from which the soul has left; completely empty (of a residence, etc.); corpse; deserted; shed skin (of a snake, insect, etc.); vacant
  - 1571980 **藻抜けの殻** [もぬけのから, もぬけのカラ] — body from which the soul has left; completely empty (of a residence, etc.); corpse; deserted; shed skin (of a snake, insect, etc.); vacant
  - 1571980 **蛻けの殻** [もぬけのから, もぬけのカラ] — body from which the soul has left; completely empty (of a residence, etc.); corpse; deserted; shed skin (of a snake, insect, etc.); vacant
  - 1571980 **蛻のから** [もぬけのから, もぬけのカラ] — body from which the soul has left; completely empty (of a residence, etc.); corpse; deserted; shed skin (of a snake, insect, etc.); vacant
  - 1571980 **蛻の殻** [もぬけのから, もぬけのカラ] — body from which the soul has left; completely empty (of a residence, etc.); corpse; deserted; shed skin (of a snake, insect, etc.); vacant
  - 1571980 **蛻の空** [もぬけのから, もぬけのカラ] — body from which the soul has left; completely empty (of a residence, etc.); corpse; deserted; shed skin (of a snake, insect, etc.); vacant
  - 1571980 **裳抜けの殻** [もぬけのから, もぬけのカラ] — body from which the soul has left; completely empty (of a residence, etc.); corpse; deserted; shed skin (of a snake, insect, etc.); vacant
  - 1571980 **裳脱けの殻** [もぬけのから, もぬけのカラ] — body from which the soul has left; completely empty (of a residence, etc.); corpse; deserted; shed skin (of a snake, insect, etc.); vacant

- Entry **1602930**: 振りかかる / 振り掛かる / 降りかかる / 降り懸かる / 降り懸る / 降り掛かる / 降り掛る / 降懸かる / 降懸る / 降掛かる / 降掛る
  - 1602930 **振りかかる** [ふりかかる] — to befall; to fall (on); to happen (to); to rain down; to shower down
  - 1602930 **振り掛かる** [ふりかかる] — to befall; to fall (on); to happen (to); to rain down; to shower down
  - 1602930 **降りかかる** [ふりかかる] — to befall; to fall (on); to happen (to); to rain down; to shower down
  - 1602930 **降り懸かる** [ふりかかる] — to befall; to fall (on); to happen (to); to rain down; to shower down
  - 1602930 **降り懸る** [ふりかかる] — to befall; to fall (on); to happen (to); to rain down; to shower down
  - 1602930 **降り掛かる** [ふりかかる] — to befall; to fall (on); to happen (to); to rain down; to shower down
  - 1602930 **降り掛る** [ふりかかる] — to befall; to fall (on); to happen (to); to rain down; to shower down
  - 1602930 **降懸かる** [ふりかかる] — to befall; to fall (on); to happen (to); to rain down; to shower down
  - 1602930 **降懸る** [ふりかかる] — to befall; to fall (on); to happen (to); to rain down; to shower down
  - 1602930 **降掛かる** [ふりかかる] — to befall; to fall (on); to happen (to); to rain down; to shower down
  - 1602930 **降掛る** [ふりかかる] — to befall; to fall (on); to happen (to); to rain down; to shower down

- Entry **1796330**: つり目 / つり眼 / ツリ目 / ツリ眼 / 吊り目 / 吊り眼 / 吊目 / 攣り目 / 釣り目 / 釣り眼 / 釣目
  - 1796330 **つり目** [つりめ] — eyes that are turned up at the outer corners; slanted eyes; upturned eyes
  - 1796330 **つり眼** [つりめ] — eyes that are turned up at the outer corners; slanted eyes; upturned eyes
  - 1796330 **ツリ目** [つりめ] — eyes that are turned up at the outer corners; slanted eyes; upturned eyes
  - 1796330 **ツリ眼** [つりめ] — eyes that are turned up at the outer corners; slanted eyes; upturned eyes
  - 1796330 **吊り目** [つりめ] — eyes that are turned up at the outer corners; slanted eyes; upturned eyes
  - 1796330 **吊り眼** [つりめ] — eyes that are turned up at the outer corners; slanted eyes; upturned eyes
  - 1796330 **吊目** [つりめ] — eyes that are turned up at the outer corners; slanted eyes; upturned eyes
  - 1796330 **攣り目** [つりめ] — eyes that are turned up at the outer corners; slanted eyes; upturned eyes
  - 1796330 **釣り目** [つりめ] — eyes that are turned up at the outer corners; slanted eyes; upturned eyes
  - 1796330 **釣り眼** [つりめ] — eyes that are turned up at the outer corners; slanted eyes; upturned eyes
  - 1796330 **釣目** [つりめ] — eyes that are turned up at the outer corners; slanted eyes; upturned eyes

- Entry **1902630**: はり合わせる / 張りあわせる / 張り合せる / 張り合わせる / 張合せる / 張合わせる / 貼りあわせる / 貼り合せる / 貼り合わせる / 貼合せる / 貼合わせる
  - 1902630 **はり合わせる** [はりあわせる] — to paste together
  - 1902630 **張りあわせる** [はりあわせる] — to paste together
  - 1902630 **張り合せる** [はりあわせる] — to paste together
  - 1902630 **張り合わせる** [はりあわせる] — to paste together
  - 1902630 **張合せる** [はりあわせる] — to paste together
  - 1902630 **張合わせる** [はりあわせる] — to paste together
  - 1902630 **貼りあわせる** [はりあわせる] — to paste together
  - 1902630 **貼り合せる** [はりあわせる] — to paste together
  - 1902630 **貼り合わせる** [はりあわせる] — to paste together
  - 1902630 **貼合せる** [はりあわせる] — to paste together
  - 1902630 **貼合わせる** [はりあわせる] — to paste together

- Entry **2125000**: ごたぶんに漏れず / ご他聞にもれず / ご他聞に漏れず / ご多分にもれず / ご多分に洩れず / ご多分に漏れず / ご多聞にもれず / ご多聞に漏れず / 御多分にもれず / 御多分に洩れず / 御多分に漏れず
  - 2125000 **ごたぶんに漏れず** [ごたぶんにもれず] — as is usual with; being no exception; in common with; just like everyone else; like many others
  - 2125000 **ご他聞にもれず** [ごたぶんにもれず] — as is usual with; being no exception; in common with; just like everyone else; like many others
  - 2125000 **ご他聞に漏れず** [ごたぶんにもれず] — as is usual with; being no exception; in common with; just like everyone else; like many others
  - 2125000 **ご多分にもれず** [ごたぶんにもれず] — as is usual with; being no exception; in common with; just like everyone else; like many others
  - 2125000 **ご多分に洩れず** [ごたぶんにもれず] — as is usual with; being no exception; in common with; just like everyone else; like many others
  - 2125000 **ご多分に漏れず** [ごたぶんにもれず] — as is usual with; being no exception; in common with; just like everyone else; like many others
  - 2125000 **ご多聞にもれず** [ごたぶんにもれず] — as is usual with; being no exception; in common with; just like everyone else; like many others
  - 2125000 **ご多聞に漏れず** [ごたぶんにもれず] — as is usual with; being no exception; in common with; just like everyone else; like many others
  - 2125000 **御多分にもれず** [ごたぶんにもれず] — as is usual with; being no exception; in common with; just like everyone else; like many others
  - 2125000 **御多分に洩れず** [ごたぶんにもれず] — as is usual with; being no exception; in common with; just like everyone else; like many others
  - 2125000 **御多分に漏れず** [ごたぶんにもれず] — as is usual with; being no exception; in common with; just like everyone else; like many others

- Entry **2416710**: うそから出たまこと / ウソから出たまこと / ウソから出たマコト / ウソから出た真 / 嘘からでたまこと / 嘘からでた真 / 嘘から出たまこと / 嘘から出たマコト / 嘘から出た実 / 嘘から出た真 / 嘘から出た誠
  - 2416710 **うそから出たまこと** [うそからでたまこと] — lie turned truth; something intended as a lie or joke which (by chance) ends up being true
  - 2416710 **ウソから出たまこと** [うそからでたまこと] — lie turned truth; something intended as a lie or joke which (by chance) ends up being true
  - 2416710 **ウソから出たマコト** [うそからでたまこと] — lie turned truth; something intended as a lie or joke which (by chance) ends up being true
  - 2416710 **ウソから出た真** [うそからでたまこと] — lie turned truth; something intended as a lie or joke which (by chance) ends up being true
  - 2416710 **嘘からでたまこと** [うそからでたまこと] — lie turned truth; something intended as a lie or joke which (by chance) ends up being true
  - 2416710 **嘘からでた真** [うそからでたまこと] — lie turned truth; something intended as a lie or joke which (by chance) ends up being true
  - 2416710 **嘘から出たまこと** [うそからでたまこと] — lie turned truth; something intended as a lie or joke which (by chance) ends up being true
  - 2416710 **嘘から出たマコト** [うそからでたまこと] — lie turned truth; something intended as a lie or joke which (by chance) ends up being true
  - 2416710 **嘘から出た実** [うそからでたまこと] — lie turned truth; something intended as a lie or joke which (by chance) ends up being true
  - 2416710 **嘘から出た真** [うそからでたまこと] — lie turned truth; something intended as a lie or joke which (by chance) ends up being true
  - 2416710 **嘘から出た誠** [うそからでたまこと] — lie turned truth; something intended as a lie or joke which (by chance) ends up being true

- Entry **2784340**: ひっかき傷 / ひっかき疵 / ひっ掻き傷 / 引っかき傷 / 引っかき疵 / 引っ掻ききず / 引っ掻き傷 / 引っ掻き疵 / 引っ搔ききず / 引っ搔き傷 / 引っ搔き疵
  - 2784340 **ひっかき傷** [ひっかききず] — scratch; scratch mark
  - 2784340 **ひっかき疵** [ひっかききず] — scratch; scratch mark
  - 2784340 **ひっ掻き傷** [ひっかききず] — scratch; scratch mark
  - 2784340 **引っかき傷** [ひっかききず] — scratch; scratch mark
  - 2784340 **引っかき疵** [ひっかききず] — scratch; scratch mark
  - 2784340 **引っ掻ききず** [ひっかききず] — scratch; scratch mark
  - 2784340 **引っ掻き傷** [ひっかききず] — scratch; scratch mark
  - 2784340 **引っ掻き疵** [ひっかききず] — scratch; scratch mark
  - 2784340 **引っ搔ききず** [ひっかききず] — scratch; scratch mark
  - 2784340 **引っ搔き傷** [ひっかききず] — scratch; scratch mark
  - 2784340 **引っ搔き疵** [ひっかききず] — scratch; scratch mark

- Entry **2859901**: 名物にうまいものなし / 名物にうまいもの無し / 名物にうまい物なし / 名物に旨いものなし / 名物に旨いもの無し / 名物に旨い物なし / 名物に旨い物無し / 名物に美味いものなし / 名物に美味いもの無し / 名物に美味い物なし / 名物に美味い物無し
  - 2859901 **名物にうまいものなし** [めいぶつにうまいものなし] — local specialties rarely taste good; the reality always falls short of the fame
  - 2859901 **名物にうまいもの無し** [めいぶつにうまいものなし] — local specialties rarely taste good; the reality always falls short of the fame
  - 2859901 **名物にうまい物なし** [めいぶつにうまいものなし] — local specialties rarely taste good; the reality always falls short of the fame
  - 2859901 **名物に旨いものなし** [めいぶつにうまいものなし] — local specialties rarely taste good; the reality always falls short of the fame
  - 2859901 **名物に旨いもの無し** [めいぶつにうまいものなし] — local specialties rarely taste good; the reality always falls short of the fame
  - 2859901 **名物に旨い物なし** [めいぶつにうまいものなし] — local specialties rarely taste good; the reality always falls short of the fame
  - 2859901 **名物に旨い物無し** [めいぶつにうまいものなし] — local specialties rarely taste good; the reality always falls short of the fame
  - 2859901 **名物に美味いものなし** [めいぶつにうまいものなし] — local specialties rarely taste good; the reality always falls short of the fame
  - 2859901 **名物に美味いもの無し** [めいぶつにうまいものなし] — local specialties rarely taste good; the reality always falls short of the fame
  - 2859901 **名物に美味い物なし** [めいぶつにうまいものなし] — local specialties rarely taste good; the reality always falls short of the fame
  - 2859901 **名物に美味い物無し** [めいぶつにうまいものなし] — local specialties rarely taste good; the reality always falls short of the fame

## 5. Reading variation inside one entry

| Значение | Количество |
| --- | --- |
| same_reading_sets | 33 066 |
| different_reading_sets | 2 738 |
| different_exact_sets_but_same_after_katakana_to_hiragana | 967 |
| at_least_one_disjoint_pair | 1 656 |
| differences_only_small_kana_normalized_distance_le_1 | 1 844 |
| small_changes_with_actual_normalized_distance_1 | 877 |
| reading_max | 13 |

Small-change definition: для каждой пары differing sets каждый reading из A имеет reading B на normalized distance ≤1 и наоборот. Это строгий bidirectional nearest-match proxy, не фонетическая/лексическая экспертиза. Он включает script-only различия; они показаны отдельным count. Max distinct readings =13 у импортированного multi-written entry.

### 30 entries с хотя бы одной парой без общего exact reading

- Entry **1004000**: くんくん鳴く / クンクン鳴く
  - 1004000 **くんくん鳴く** [くんくんなく] — to whine (of a dog)
  - 1004000 **クンクン鳴く** [クンクンなく] — to whine (of a dog)

- Entry **1004220**: けらけら笑う / ケラケラ笑う
  - 1004220 **けらけら笑う** [けらけらわらう] — to cackle
  - 1004220 **ケラケラ笑う** [ケラケラわらう] — to cackle

- Entry **1004270**: げらげら笑う / ゲラゲラ笑う
  - 1004270 **げらげら笑う** [げらげらわらう] — to guffaw; to laugh heartily; to roar with laughter
  - 1004270 **ゲラゲラ笑う** [ゲラゲラわらう] — to guffaw; to laugh heartily; to roar with laughter

- Entry **1009510**: 何じゃそら / 何じゃそりゃ
  - 1009510 **何じゃそら** [なんじゃそら] — what the ...; what's that?
  - 1009510 **何じゃそりゃ** [なんじゃそりゃ] — what the ...; what's that?

- Entry **1010840**: ぴかぴか光る / ピカピカ光る
  - 1010840 **ぴかぴか光る** [ぴかぴかひかる] — to glitter; to sparkle; to twinkle
  - 1010840 **ピカピカ光る** [ピカピカひかる] — to glitter; to sparkle; to twinkle

- Entry **1010850**: ぴくぴく動く / ピクピク動く
  - 1010850 **ぴくぴく動く** [ぴくぴくうごく] — to jerk; to twitch; to wiggle
  - 1010850 **ピクピク動く** [ピクピクうごく] — to jerk; to twitch; to wiggle

- Entry **1010930**: ぴよぴよ鳴く / ピヨピヨ鳴く
  - 1010930 **ぴよぴよ鳴く** [ぴよぴよなく] — to cheep; to peep
  - 1010930 **ピヨピヨ鳴く** [ピヨピヨなく] — to cheep; to peep

- Entry **1011520**: べろべろ嘗める / べろべろ舐める / ベロベロ舐める
  - 1011520 **べろべろ嘗める** [べろべろなめる] — to lap something up; to lick something up
  - 1011520 **べろべろ舐める** [べろべろなめる] — to lap something up; to lick something up
  - 1011520 **ベロベロ舐める** [ベロベロなめる] — to lap something up; to lick something up

- Entry **1011970**: ぽたぽた落ちる / ポタポタ落ちる
  - 1011970 **ぽたぽた落ちる** [ぽたぽたおちる] — to drip down; to fall in drops; to trickle down
  - 1011970 **ポタポタ落ちる** [ポタポタおちる] — to drip down; to fall in drops; to trickle down

- Entry **1012040**: ぽん引き / ポン引き
  - 1012040 **ぽん引き** [ぽんびき, ぽんぴき] — con-man; pimp; swindler; tout
  - 1012040 **ポン引き** [ポンびき, ポンぴき] — con-man; pimp; swindler; tout

- Entry **1021330**: イスラム原理主義 / イスラーム原理主義
  - 1021330 **イスラム原理主義** [イスラムげんりしゅぎ] — Islamic fundamentalism
  - 1021330 **イスラーム原理主義** [イスラームげんりしゅぎ] — Islamic fundamentalism

- Entry **1034170**: オギノ式 / 荻野式
  - 1034170 **オギノ式** [オギノしき] — Ogino method (of birth control); rhythm method
  - 1034170 **荻野式** [おぎのしき] — Ogino method (of birth control); rhythm method

- Entry **1037740**: かつ丼 / カツ丼
  - 1037740 **かつ丼** [かつどん] — katsudon; pork cutlet served on top of a bowl of rice
  - 1037740 **カツ丼** [カツどん] — katsudon; pork cutlet served on top of a bowl of rice

- Entry **1038000**: カバー曲 / カヴァー曲
  - 1038000 **カバー曲** [カバーきょく] — cover song (remake of another artist's song)
  - 1038000 **カヴァー曲** [カヴァーきょく] — cover song (remake of another artist's song)

- Entry **1038270**: カポシ肉腫 / カポジ肉腫
  - 1038270 **カポシ肉腫** [カポシにくしゅ] — Kaposi's sarcoma
  - 1038270 **カポジ肉腫** [カポジにくしゅ] — Kaposi's sarcoma

- Entry **1043200**: ギリシア神話 / ギリシャ神話
  - 1043200 **ギリシア神話** [ギリシアしんわ] — Greek mythology
  - 1043200 **ギリシャ神話** [ギリシャしんわ] — Greek mythology

- Entry **1043210**: ギリシア文字 / ギリシャ文字
  - 1043210 **ギリシア文字** [ギリシアもじ] — Greek alphabet; Greek letter
  - 1043210 **ギリシャ文字** [ギリシャもじ] — Greek alphabet; Greek letter

- Entry **1053560**: コンピューター化 / コンピュータ化
  - 1053560 **コンピューター化** [コンピューターか] — computerisation; computerization
  - 1053560 **コンピュータ化** [コンピュータか] — computerisation; computerization

- Entry **1053570**: コンピューター用語 / コンピュータ用語
  - 1053570 **コンピューター用語** [コンピューターようご] — computer term; computer terminology
  - 1053570 **コンピュータ用語** [コンピュータようご] — computer term; computer terminology

- Entry **1054580**: ごむの木 / ゴムの木 / 護謨の木
  - 1054580 **ごむの木** [ごむのき] — rubber tree
  - 1054580 **ゴムの木** [ゴムのき] — rubber tree
  - 1054580 **護謨の木** [ごむのき] — rubber tree

- Entry **1061740**: しゃぼん玉 / シャボン玉
  - 1061740 **しゃぼん玉** [しゃぼんだま] — soap bubble
  - 1061740 **シャボン玉** [シャボンだま] — soap bubble

- Entry **1068960**: スケ番 / 助番
  - 1068960 **スケ番** [スケばん] — leader of a female gang; leader of a group of delinquent girls
  - 1068960 **助番** [すけばん] — leader of a female gang; leader of a group of delinquent girls

- Entry **1077700**: ちくちく痛む / チクチク痛む
  - 1077700 **ちくちく痛む** [ちくちくいたむ] — to prickle; to tingle
  - 1077700 **チクチク痛む** [チクチクいたむ] — to prickle; to tingle

- Entry **1083750**: デフォルト値 / デフォールト値
  - 1083750 **デフォルト値** [デフォルトち] — default value
  - 1083750 **デフォールト値** [デフォールトち] — default value

- Entry **1087390**: トロイの木馬 / トロイアの木馬
  - 1087390 **トロイの木馬** [トロイのもくば] — Trojan horse
  - 1087390 **トロイアの木馬** [トロイアのもくば] — Trojan horse

- Entry **1103690**: ヒグス機構 / ヒッグス機構
  - 1103690 **ヒグス機構** [ヒグスきこう] — Higgs mechanism
  - 1103690 **ヒッグス機構** [ヒッグスきこう] — Higgs mechanism

- Entry **1105980**: びらん剤 / ビラン剤 / 糜爛剤
  - 1105980 **びらん剤** [びらんざい] — blister agent; vesicant
  - 1105980 **ビラン剤** [ビランざい] — blister agent; vesicant
  - 1105980 **糜爛剤** [びらんざい] — blister agent; vesicant

- Entry **1108140**: ファジィ理論 / ファジー理論
  - 1108140 **ファジィ理論** [ファジィりろん] — fuzzy theory
  - 1108140 **ファジー理論** [ファジーりろん] — fuzzy theory

- Entry **1115120**: ぷー太郎 / プー太郎 / 風太郎
  - 1115120 **ぷー太郎** [ぷーたろう] — floater; unemployed person; vagabond; vagrant
  - 1115120 **プー太郎** [プーたろう] — floater; unemployed person; vagabond; vagrant
  - 1115120 **風太郎** [ふうたろう, ぷうたろう] — day labourer (esp. on the docks); floater; unemployed person; vagabond; vagrant

- Entry **1121330**: ペルシア語 / ペルシャ語
  - 1121330 **ペルシア語** [ペルシアご] — Farsi; Persian (language)
  - 1121330 **ペルシャ語** [ペルシャご] — Farsi; Persian (language)

### 30 small-change entries

- Entry **1004000**: くんくん鳴く / クンクン鳴く
  - 1004000 **くんくん鳴く** [くんくんなく] — to whine (of a dog)
  - 1004000 **クンクン鳴く** [クンクンなく] — to whine (of a dog)

- Entry **1004220**: けらけら笑う / ケラケラ笑う
  - 1004220 **けらけら笑う** [けらけらわらう] — to cackle
  - 1004220 **ケラケラ笑う** [ケラケラわらう] — to cackle

- Entry **1004270**: げらげら笑う / ゲラゲラ笑う
  - 1004270 **げらげら笑う** [げらげらわらう] — to guffaw; to laugh heartily; to roar with laughter
  - 1004270 **ゲラゲラ笑う** [ゲラゲラわらう] — to guffaw; to laugh heartily; to roar with laughter

- Entry **1004740**: この度 / 此の度 / 此度
  - 1004740 **この度** [このたび] — at this time; now; this occasion
  - 1004740 **此の度** [このたび] — at this time; now; this occasion
  - 1004740 **此度** [こたび, このたび] — at this time; now; this occasion

- Entry **1010230**: 半平 / 半片
  - 1010230 **半平** [はんぺい, はんぺん] — pounded fish cake
  - 1010230 **半片** [はんぺん] — half a slice; half a ticket; pounded fish cake; ticket stub

- Entry **1010840**: ぴかぴか光る / ピカピカ光る
  - 1010840 **ぴかぴか光る** [ぴかぴかひかる] — to glitter; to sparkle; to twinkle
  - 1010840 **ピカピカ光る** [ピカピカひかる] — to glitter; to sparkle; to twinkle

- Entry **1010850**: ぴくぴく動く / ピクピク動く
  - 1010850 **ぴくぴく動く** [ぴくぴくうごく] — to jerk; to twitch; to wiggle
  - 1010850 **ピクピク動く** [ピクピクうごく] — to jerk; to twitch; to wiggle

- Entry **1010930**: ぴよぴよ鳴く / ピヨピヨ鳴く
  - 1010930 **ぴよぴよ鳴く** [ぴよぴよなく] — to cheep; to peep
  - 1010930 **ピヨピヨ鳴く** [ピヨピヨなく] — to cheep; to peep

- Entry **1011520**: べろべろ嘗める / べろべろ舐める / ベロベロ舐める
  - 1011520 **べろべろ嘗める** [べろべろなめる] — to lap something up; to lick something up
  - 1011520 **べろべろ舐める** [べろべろなめる] — to lap something up; to lick something up
  - 1011520 **ベロベロ舐める** [ベロベロなめる] — to lap something up; to lick something up

- Entry **1011970**: ぽたぽた落ちる / ポタポタ落ちる
  - 1011970 **ぽたぽた落ちる** [ぽたぽたおちる] — to drip down; to fall in drops; to trickle down
  - 1011970 **ポタポタ落ちる** [ポタポタおちる] — to drip down; to fall in drops; to trickle down

- Entry **1012040**: ぽん引き / ポン引き
  - 1012040 **ぽん引き** [ぽんびき, ぽんぴき] — con-man; pimp; swindler; tout
  - 1012040 **ポン引き** [ポンびき, ポンぴき] — con-man; pimp; swindler; tout

- Entry **1012680**: 野衾 / 鼯鼠
  - 1012680 **野衾** [ももんが] — Japanese dwarf flying squirrel; Japanese flying squirrel (Pteromys momonga); small Japanese flying squirrel
  - 1012680 **鼯鼠** [ももんが, ももんがあ] — Japanese dwarf flying squirrel; Japanese flying squirrel (Pteromys momonga); small Japanese flying squirrel

- Entry **1021150**: いきが良い / イキが良い / 活きがいい / 活きがよい / 活きが良い / 生きがいい / 生きがよい / 生きが良い
  - 1021150 **いきが良い** [いきがいい] — animated; fresh (e.g. fish); full of energy; lively; spirited
  - 1021150 **イキが良い** [いきがいい] — animated; fresh (e.g. fish); full of energy; lively; spirited
  - 1021150 **活きがいい** [いきがいい] — animated; fresh (e.g. fish); full of energy; lively; spirited
  - 1021150 **活きがよい** [いきがいい] — animated; fresh (e.g. fish); full of energy; lively; spirited
  - 1021150 **活きが良い** [いきがいい, いきがよい] — animated; fresh (e.g. fish); full of energy; lively; spirited
  - 1021150 **生きがいい** [いきがいい] — animated; fresh (e.g. fish); full of energy; lively; spirited
  - 1021150 **生きがよい** [いきがいい] — animated; fresh (e.g. fish); full of energy; lively; spirited
  - 1021150 **生きが良い** [いきがいい, いきがよい] — animated; fresh (e.g. fish); full of energy; lively; spirited

- Entry **1021330**: イスラム原理主義 / イスラーム原理主義
  - 1021330 **イスラム原理主義** [イスラムげんりしゅぎ] — Islamic fundamentalism
  - 1021330 **イスラーム原理主義** [イスラームげんりしゅぎ] — Islamic fundamentalism

- Entry **1034170**: オギノ式 / 荻野式
  - 1034170 **オギノ式** [オギノしき] — Ogino method (of birth control); rhythm method
  - 1034170 **荻野式** [おぎのしき] — Ogino method (of birth control); rhythm method

- Entry **1037740**: かつ丼 / カツ丼
  - 1037740 **かつ丼** [かつどん] — katsudon; pork cutlet served on top of a bowl of rice
  - 1037740 **カツ丼** [カツどん] — katsudon; pork cutlet served on top of a bowl of rice

- Entry **1038270**: カポシ肉腫 / カポジ肉腫
  - 1038270 **カポシ肉腫** [カポシにくしゅ] — Kaposi's sarcoma
  - 1038270 **カポジ肉腫** [カポジにくしゅ] — Kaposi's sarcoma

- Entry **1043200**: ギリシア神話 / ギリシャ神話
  - 1043200 **ギリシア神話** [ギリシアしんわ] — Greek mythology
  - 1043200 **ギリシャ神話** [ギリシャしんわ] — Greek mythology

- Entry **1043210**: ギリシア文字 / ギリシャ文字
  - 1043210 **ギリシア文字** [ギリシアもじ] — Greek alphabet; Greek letter
  - 1043210 **ギリシャ文字** [ギリシャもじ] — Greek alphabet; Greek letter

- Entry **1053560**: コンピューター化 / コンピュータ化
  - 1053560 **コンピューター化** [コンピューターか] — computerisation; computerization
  - 1053560 **コンピュータ化** [コンピュータか] — computerisation; computerization

- Entry **1053570**: コンピューター用語 / コンピュータ用語
  - 1053570 **コンピューター用語** [コンピューターようご] — computer term; computer terminology
  - 1053570 **コンピュータ用語** [コンピュータようご] — computer term; computer terminology

- Entry **1054580**: ごむの木 / ゴムの木 / 護謨の木
  - 1054580 **ごむの木** [ごむのき] — rubber tree
  - 1054580 **ゴムの木** [ゴムのき] — rubber tree
  - 1054580 **護謨の木** [ごむのき] — rubber tree

- Entry **1061740**: しゃぼん玉 / シャボン玉
  - 1061740 **しゃぼん玉** [しゃぼんだま] — soap bubble
  - 1061740 **シャボン玉** [シャボンだま] — soap bubble

- Entry **1068960**: スケ番 / 助番
  - 1068960 **スケ番** [スケばん] — leader of a female gang; leader of a group of delinquent girls
  - 1068960 **助番** [すけばん] — leader of a female gang; leader of a group of delinquent girls

- Entry **1077700**: ちくちく痛む / チクチク痛む
  - 1077700 **ちくちく痛む** [ちくちくいたむ] — to prickle; to tingle
  - 1077700 **チクチク痛む** [チクチクいたむ] — to prickle; to tingle

- Entry **1083750**: デフォルト値 / デフォールト値
  - 1083750 **デフォルト値** [デフォルトち] — default value
  - 1083750 **デフォールト値** [デフォールトち] — default value

- Entry **1087390**: トロイの木馬 / トロイアの木馬
  - 1087390 **トロイの木馬** [トロイのもくば] — Trojan horse
  - 1087390 **トロイアの木馬** [トロイアのもくば] — Trojan horse

- Entry **1103690**: ヒグス機構 / ヒッグス機構
  - 1103690 **ヒグス機構** [ヒグスきこう] — Higgs mechanism
  - 1103690 **ヒッグス機構** [ヒッグスきこう] — Higgs mechanism

- Entry **1105980**: びらん剤 / ビラン剤 / 糜爛剤
  - 1105980 **びらん剤** [びらんざい] — blister agent; vesicant
  - 1105980 **ビラン剤** [ビランざい] — blister agent; vesicant
  - 1105980 **糜爛剤** [びらんざい] — blister agent; vesicant

- Entry **1108140**: ファジィ理論 / ファジー理論
  - 1108140 **ファジィ理論** [ファジィりろん] — fuzzy theory
  - 1108140 **ファジー理論** [ファジーりろん] — fuzzy theory

Примеры в начале включают hiragana/katakana changes. В JSON есть также reading_differences; отсутствие общего raw reading не всегда означает разные normalized readings.

### Actual normalized single-edit examples внутри одного entry

**long-vowel-like insertion/deletion (proxy)**

- A: 1021330 **イスラム原理主義** [イスラムげんりしゅぎ] — Islamic fundamentalism
  - B: 1021330 **イスラーム原理主義** [イスラームげんりしゅぎ] — Islamic fundamentalism
  - Normalized readings: いすらむげんりしゅぎ ↔ いすらーむげんりしゅぎ

- A: 1053560 **コンピューター化** [コンピューターか] — computerisation; computerization
  - B: 1053560 **コンピュータ化** [コンピュータか] — computerisation; computerization
  - Normalized readings: こんぴゅーたーか ↔ こんぴゅーたか

- A: 1053570 **コンピューター用語** [コンピューターようご] — computer term; computer terminology
  - B: 1053570 **コンピュータ用語** [コンピュータようご] — computer term; computer terminology
  - Normalized readings: こんぴゅーたーようご ↔ こんぴゅーたようご

- A: 1083750 **デフォルト値** [デフォルトち] — default value
  - B: 1083750 **デフォールト値** [デフォールトち] — default value
  - Normalized readings: でふぉるとち ↔ でふぉーるとち

- A: 1178640 **塩化ビニル** [えんかビニル] — chloroethylene; vinyl chloride
  - B: 1178640 **塩化ビニール** [えんかビニール] — chloroethylene; vinyl chloride
  - Normalized readings: えんかびにる ↔ えんかびにーる

**voiced/unvoiced or semi-voiced kana**

- A: 1038270 **カポシ肉腫** [カポシにくしゅ] — Kaposi's sarcoma
  - B: 1038270 **カポジ肉腫** [カポジにくしゅ] — Kaposi's sarcoma
  - Normalized readings: かぽしにくしゅ ↔ かぽじにくしゅ

- A: 1305730 **使いかって** [つかいかって] — convenience; ease of use; usability; user-friendliness; utility
  - B: 1305730 **使いがって** [つかいがって] — convenience; ease of use; usability; user-friendliness; utility
  - Normalized readings: つかいかって ↔ つかいがって

- A: 1393950 **先夫** [せんぷ] — former husband; late husband
  - B: 1393950 **前夫** [ぜんぷ] — former husband; late husband
  - Normalized readings: せんぷ ↔ ぜんぷ

- A: 1393960 **先婦** [せんぷ] — late wife; previous wife
  - B: 1393960 **前婦** [ぜんぷ] — late wife; previous wife
  - Normalized readings: せんぷ ↔ ぜんぷ

- A: 1488360 **百日せき** [ひゃくにちせき] — pertussis; whooping cough
  - B: 1488360 **百日ぜき** [ひゃくにちぜき] — pertussis; whooping cough
  - Normalized readings: ひゃくにちせき ↔ ひゃくにちぜき

**other substitution**

- A: 1043200 **ギリシア神話** [ギリシアしんわ] — Greek mythology
  - B: 1043200 **ギリシャ神話** [ギリシャしんわ] — Greek mythology
  - Normalized readings: ぎりしあしんわ ↔ ぎりしゃしんわ

- A: 1043210 **ギリシア文字** [ギリシアもじ] — Greek alphabet; Greek letter
  - B: 1043210 **ギリシャ文字** [ギリシャもじ] — Greek alphabet; Greek letter
  - Normalized readings: ぎりしあもじ ↔ ぎりしゃもじ

- A: 1108140 **ファジィ理論** [ファジィりろん] — fuzzy theory
  - B: 1108140 **ファジー理論** [ファジーりろん] — fuzzy theory
  - Normalized readings: ふぁじぃりろん ↔ ふぁじーりろん

- A: 1121330 **ペルシア語** [ペルシアご] — Farsi; Persian (language)
  - B: 1121330 **ペルシャ語** [ペルシャご] — Farsi; Persian (language)
  - Normalized readings: ぺるしあご ↔ ぺるしゃご

- A: 1150900 **愛想のいい** [あいそのいい] — affable; agreeable; amiable; sociable
  - B: 1150900 **愛想のよい** [あいそのよい] — affable; agreeable; amiable; sociable
  - Normalized readings: あいそのいい ↔ あいそのよい

**other insertion/deletion**

- A: 1087390 **トロイの木馬** [トロイのもくば] — Trojan horse
  - B: 1087390 **トロイアの木馬** [トロイアのもくば] — Trojan horse
  - Normalized readings: とろいのもくば ↔ とろいあのもくば

- A: 1391700 **つむじ風** [つむじかぜ] — whirlwind
  - B: 1391700 **辻風** [つじかぜ] — whirlwind
  - Normalized readings: つむじかぜ ↔ つじかぜ

- A: 1581990 **つまみ取る** [つまみとる] — to nip off; to pick; to pluck
  - B: 1581990 **つみ取る** [つみとる] — to nip off; to pick; to pluck
  - Normalized readings: つまみとる ↔ つみとる

- A: 1595860 **こすり付ける** [こすりつける] — to brush on; to nuzzle against (e.g. dog nosing a person); to press against; to push against; to rub (something) onto; to smear with; to strike (a match); to wipe onto
  - B: 1595860 **摩り付ける** [すりつける] — to brush on; to nuzzle against (e.g. dog nosing a person); to press against; to push against; to rub (something) onto; to smear with; to strike (a match); to wipe onto
  - Normalized readings: こすりつける ↔ すりつける

- A: 1595860 **こすり付ける** [こすりつける] — to brush on; to nuzzle against (e.g. dog nosing a person); to press against; to push against; to rub (something) onto; to smear with; to strike (a match); to wipe onto
  - B: 1595860 **摺り付ける** [すりつける] — to brush on; to nuzzle against (e.g. dog nosing a person); to press against; to push against; to rub (something) onto; to smear with; to strike (a match); to wipe onto
  - Normalized readings: こすりつける ↔ すりつける

**small-tsu insertion/deletion**

- A: 1103690 **ヒグス機構** [ヒグスきこう] — Higgs mechanism
  - B: 1103690 **ヒッグス機構** [ヒッグスきこう] — Higgs mechanism
  - Normalized readings: ひぐすきこう ↔ ひっぐすきこう

- A: 1229820 **泣きっ面に蜂** [なきっつらにはち] — adding insult to injury; bees to a tearful face; making matters worse
  - B: 1229820 **泣き面に蜂** [なきつらにはち] — adding insult to injury; bees to a tearful face; making matters worse
  - Normalized readings: なきっつらにはち ↔ なきつらにはち

- A: 1366670 **人なつこい** [ひとなつこい] — (animals) taking kindly to people; affable; amiable; friendly; loving company; sociable
  - B: 1366670 **人なつっこい** [ひとなつっこい] — (animals) taking kindly to people; affable; amiable; friendly; loving company; sociable
  - Normalized readings: ひとなつこい ↔ ひとなつっこい

- A: 1366670 **人なつこい** [ひとなつこい] — (animals) taking kindly to people; affable; amiable; friendly; loving company; sociable
  - B: 1366670 **人懐っこい** [ひとなつっこい] — (animals) taking kindly to people; affable; amiable; friendly; loving company; sociable
  - Normalized readings: ひとなつこい ↔ ひとなつっこい

- A: 1366670 **人なつっこい** [ひとなつっこい] — (animals) taking kindly to people; affable; amiable; friendly; loving company; sociable
  - B: 1366670 **人懐こい** [ひとなつこい] — (animals) taking kindly to people; affable; amiable; friendly; loving company; sociable
  - Normalized readings: ひとなつっこい ↔ ひとなつこい

**long-vowel-like substitution (proxy)**

- A: 1115120 **ぷー太郎** [ぷーたろう] — floater; unemployed person; vagabond; vagrant
  - B: 1115120 **風太郎** [ふうたろう, ぷうたろう] — day labourer (esp. on the docks); floater; unemployed person; vagabond; vagrant
  - Normalized readings: ぷーたろう ↔ ぷうたろう

- A: 1115120 **プー太郎** [プーたろう] — floater; unemployed person; vagabond; vagrant
  - B: 1115120 **風太郎** [ふうたろう, ぷうたろう] — day labourer (esp. on the docks); floater; unemployed person; vagabond; vagrant
  - Normalized readings: ぷーたろう ↔ ぷうたろう

- A: 1503130 **分液ろうと** [ぶんえきろうと] — separating funnel; separatory funnel
  - B: 1503130 **分液ロート** [ぶんえきロート] — separating funnel; separatory funnel
  - Normalized readings: ぶんえきろうと ↔ ぶんえきろーと

- A: 1503130 **分液ロート** [ぶんえきロート] — separating funnel; separatory funnel
  - B: 1503130 **分液漏斗** [ぶんえきろうと] — separating funnel; separatory funnel
  - Normalized readings: ぶんえきろーと ↔ ぶんえきろうと

- A: 1916930 **ローソクの芯** [ローソクのしん] — wick of a candle
  - B: 1916930 **蝋燭の心** [ろうそくのしん] — wick of a candle
  - Normalized readings: ろーそくのしん ↔ ろうそくのしん

### Entries с максимальным количеством readings

- Entry **1565440**: 吁 / 嗚呼 / 嗟 / 嗟乎 / 噫 / 於乎 / 於戯
  - 1565440 **吁** [あ〜, あ〜あ, あ〜っ, あぁ, ああ, ああぁ, ああっ, ああー, あー, あーっ, アァ, アア, アー] — aah; ah!; alas!; argh; gah; gotcha; hey!; indeed; oh!; right; that is correct; uh huh; yeah yeah; yes; yo!
  - 1565440 **嗚呼** [あ〜, あ〜あ, あ〜っ, あぁ, ああ, ああぁ, ああっ, ああー, あー, あーっ, アァ, アア, アー] — aah; ah!; alas!; argh; gah; gotcha; hey!; indeed; oh!; right; that is correct; uh huh; yeah yeah; yes; yo!
  - 1565440 **嗟** [あ〜, あ〜あ, あ〜っ, あぁ, ああ, ああぁ, ああっ, ああー, あー, あーっ, アァ, アア, アー] — aah; ah!; alas!; argh; gah; gotcha; hey!; indeed; oh!; right; that is correct; uh huh; yeah yeah; yes; yo!
  - 1565440 **嗟乎** [あ〜, あ〜あ, あ〜っ, あぁ, ああ, ああぁ, ああっ, ああー, あー, あーっ, アァ, アア, アー] — aah; ah!; alas!; argh; gah; gotcha; hey!; indeed; oh!; right; that is correct; uh huh; yeah yeah; yes; yo!
  - 1565440 **噫** [あ〜, あ〜あ, あ〜っ, あぁ, ああ, ああぁ, ああっ, ああー, あー, あーっ, アァ, アア, アー] — aah; ah!; alas!; argh; gah; gotcha; hey!; indeed; oh!; right; that is correct; uh huh; yeah yeah; yes; yo!
  - 1565440 **於乎** [あ〜, あ〜あ, あ〜っ, あぁ, ああ, ああぁ, ああっ, ああー, あー, あーっ, アァ, アア, アー] — aah; ah!; alas!; argh; gah; gotcha; hey!; indeed; oh!; right; that is correct; uh huh; yeah yeah; yes; yo!
  - 1565440 **於戯** [あ〜, あ〜あ, あ〜っ, あぁ, ああ, ああぁ, ああっ, ああー, あー, あーっ, アァ, アア, アー] — aah; ah!; alas!; argh; gah; gotcha; hey!; indeed; oh!; right; that is correct; uh huh; yeah yeah; yes; yo!

- Entry **1421970**: 晩い / 遅い / 遲い / 鈍い
  - 1421970 **晩い** [おせー, おそい, おそーい, おっせぇ, おっせえ, おっせー, おっそい, おっそーい] — late; slow; too late
  - 1421970 **遅い** [おせー, おそい, おそーい, おっせぇ, おっせえ, おっせー, おっそい, おっそーい] — late; slow; too late
  - 1421970 **遲い** [おせー, おそい, おそーい, おっせぇ, おっせえ, おっせー, おっそい, おっそーい] — late; slow; too late
  - 1421970 **鈍い** [おせー, おそい, おそーい, おっせぇ, おっせえ, おっせー, おっそい, おっそーい] — late; slow; too late

- Entry **2826632**: ウィンナー珈琲 / ウィンナ珈琲 / ウインナー珈琲 / ウインナ珈琲
  - 2826632 **ウィンナー珈琲** [ウィンナコーヒー, ウィンナ・コーヒー, ウィンナーコーヒー, ウィンナー・コーヒー, ウインナコーヒー, ウインナ・コーヒー, ウインナーコーヒー, ウインナー・コーヒー] — Vienna coffee
  - 2826632 **ウィンナ珈琲** [ウィンナコーヒー, ウィンナ・コーヒー, ウィンナーコーヒー, ウィンナー・コーヒー, ウインナコーヒー, ウインナ・コーヒー, ウインナーコーヒー, ウインナー・コーヒー] — Vienna coffee
  - 2826632 **ウインナー珈琲** [ウィンナコーヒー, ウィンナ・コーヒー, ウィンナーコーヒー, ウィンナー・コーヒー, ウインナコーヒー, ウインナ・コーヒー, ウインナーコーヒー, ウインナー・コーヒー] — Vienna coffee
  - 2826632 **ウインナ珈琲** [ウィンナコーヒー, ウィンナ・コーヒー, ウィンナーコーヒー, ウィンナー・コーヒー, ウインナコーヒー, ウインナ・コーヒー, ウインナーコーヒー, ウインナー・コーヒー] — Vienna coffee

### Длины alternative forms

| Code-point length | Form occurrences внутри multi-written entries |
| --- | --- |
| 1 | 2 517 |
| 2 | 21 745 |
| 3 | 22 773 |
| 4 | 20 705 |
| 5 | 12 296 |
| 6+ | 11 339 |

| Entry maximum length | Entries |
| --- | --- |
| ≤2 | 7204 |
| ≤3 | 15704 |
| ≥4 | 20100 |
| ≥6 | 5558 |
| Maximum | 27 |

У 56.14% multi-written entries есть written ≥4. Гипотеза «variants в основном только короткие» этими данными не подтверждается; короткие ≤3 есть у 43.86% по entry maximum. Counts форм здесь включают каждое (entry,written), а не global distinct written.

- Entry **2832738**: 身体髪膚これを父母に受くあえて毀傷せざるは孝の始なり / 身体髪膚これを父母に受くあえて毀傷せざるは孝の始めなり
  - 2832738 **身体髪膚これを父母に受くあえて毀傷せざるは孝の始なり** [しんたいはっぷこれをふぼにうくあえてきしょうせざるはこうのはじめなり] — filial piety begins with not harming one's own body (as one's entire body was given by one's parents)
  - 2832738 **身体髪膚これを父母に受くあえて毀傷せざるは孝の始めなり** [しんたいはっぷこれをふぼにうくあえてきしょうせざるはこうのはじめなり] — filial piety begins with not harming one's own body (as one's entire body was given by one's parents)

- Entry **2846795**: グレートブリテンおよび北アイルランド連合王国 / グレートブリテン及び北アイルランド連合王国 / グレートブリテン及北アイルランド連合王国
  - 2846795 **グレートブリテンおよび北アイルランド連合王国** [グレートブリテンおよびきたアイルランドれんごうおうこく] — United Kingdom of Great Britain and Northern Ireland
  - 2846795 **グレートブリテン及び北アイルランド連合王国** [グレートブリテンおよびきたアイルランドれんごうおうこく] — United Kingdom of Great Britain and Northern Ireland
  - 2846795 **グレートブリテン及北アイルランド連合王国** [グレートブリテンおよびきたアイルランドれんごうおうこく] — United Kingdom of Great Britain and Northern Ireland

- Entry **2833961**: 梅は食うとも核食うな、中に天神寝てござる / 梅は食うとも核食うな中に天神寝てござる
  - 2833961 **梅は食うとも核食うな、中に天神寝てござる** [うめはくうともさねくうな、なかにてんじんねてござる] — don't eat plum pits (because they are poisonous); if you eat a plum, don't eat the kernel; inside it heavenly gods sleep
  - 2833961 **梅は食うとも核食うな中に天神寝てござる** [うめはくうともさねくうな、なかにてんじんねてござる] — don't eat plum pits (because they are poisonous); if you eat a plum, don't eat the kernel; inside it heavenly gods sleep

- Entry **2839580**: セントビンセントおよびグレナディーン諸島 / セントビンセント及びグレナディーン諸島
  - 2839580 **セントビンセントおよびグレナディーン諸島** [セントビンセントおよびグレナディーンしょとう] — Saint Vincent and the Grenadines
  - 2839580 **セントビンセント及びグレナディーン諸島** [セントビンセントおよびグレナディーンしょとう] — Saint Vincent and the Grenadines

- Entry **2833702**: 指切りげんまん、嘘ついたら針千本飲ます / 指切りげんまん嘘ついたら針千本飲ます / 指切拳万、嘘ついたら針千本飲ます / 指切拳万嘘ついたら針千本飲ます
  - 2833702 **指切りげんまん、嘘ついたら針千本飲ます** [ゆびきりげんまん、うそついたらはりせんぼんのます] — cross my heart and hope to die, stick a needle in my eye; pinky promise pledge: if you're lying, I'll make you drink a thousand needles
  - 2833702 **指切りげんまん嘘ついたら針千本飲ます** [ゆびきりげんまん、うそついたらはりせんぼんのます] — cross my heart and hope to die, stick a needle in my eye; pinky promise pledge: if you're lying, I'll make you drink a thousand needles
  - 2833702 **指切拳万、嘘ついたら針千本飲ます** [ゆびきりげんまん、うそついたらはりせんぼんのます] — cross my heart and hope to die, stick a needle in my eye; pinky promise pledge: if you're lying, I'll make you drink a thousand needles
  - 2833702 **指切拳万嘘ついたら針千本飲ます** [ゆびきりげんまん、うそついたらはりせんぼんのます] — cross my heart and hope to die, stick a needle in my eye; pinky promise pledge: if you're lying, I'll make you drink a thousand needles

- Entry **2859282**: こどもは三歳までに一生分の親孝行をする / こどもは３歳までに一生分の親孝行をする / 子どもは三歳までに一生分の親孝行をする / 子どもは３歳までに一生分の親孝行をする / 子供は三歳までに一生分の親孝行をする / 子供は３歳までに一生分の親孝行をする
  - 2859282 **こどもは三歳までに一生分の親孝行をする** [こどもはさんさいまでにいっしょうぶんのおやこうこうをする] — children pay back their debt to their parents in full by age 3 (through their cuteness)
  - 2859282 **こどもは３歳までに一生分の親孝行をする** [こどもはさんさいまでにいっしょうぶんのおやこうこうをする] — children pay back their debt to their parents in full by age 3 (through their cuteness)
  - 2859282 **子どもは三歳までに一生分の親孝行をする** [こどもはさんさいまでにいっしょうぶんのおやこうこうをする] — children pay back their debt to their parents in full by age 3 (through their cuteness)
  - 2859282 **子どもは３歳までに一生分の親孝行をする** [こどもはさんさいまでにいっしょうぶんのおやこうこうをする] — children pay back their debt to their parents in full by age 3 (through their cuteness)
  - 2859282 **子供は三歳までに一生分の親孝行をする** [こどもはさんさいまでにいっしょうぶんのおやこうこうをする] — children pay back their debt to their parents in full by age 3 (through their cuteness)
  - 2859282 **子供は３歳までに一生分の親孝行をする** [こどもはさんさいまでにいっしょうぶんのおやこうこうをする] — children pay back their debt to their parents in full by age 3 (through their cuteness)

- Entry **2419160**: 桃李もの言わざれども下自ら蹊を成す / 桃李物言わざれども下自ずから蹊を成す / 桃李物言わざれども下自ら蹊を成す
  - 2419160 **桃李もの言わざれども下自ら蹊を成す** [とうりものいわざれどもしたおのずからみちをなす] — a man of virtue will naturally attract admirers; good wine speaks for itself
  - 2419160 **桃李物言わざれども下自ずから蹊を成す** [とうりものいわざれどもしたおのずからみちをなす] — a man of virtue will naturally attract admirers; good wine speaks for itself
  - 2419160 **桃李物言わざれども下自ら蹊を成す** [とうりものいわざれどもしたおのずからみちをなす] — a man of virtue will naturally attract admirers; good wine speaks for itself

- Entry **2806450**: どれにしようかな天の神様のいうとおり / どれにしようかな天の神様の言う通り
  - 2806450 **どれにしようかな天の神様のいうとおり** [どれにしようかなてんのかみさまのいうとおり] — eenie, meenie, minie, moe; eeny, meeny, miny, moe
  - 2806450 **どれにしようかな天の神様の言う通り** [どれにしようかなてんのかみさまのいうとおり] — eenie, meenie, minie, moe; eeny, meeny, miny, moe

## 6. Same-reading cross-entry statistics

| Значение | Количество |
| --- | --- |
| 1 | 116 447 |
| 3-5 | 15 260 |
| 2 | 25 904 |
| 6-10 | 2 436 |
| >10 | 425 |

Maximum exact reading corresponds to 52 distinct written. Different-written + different-entry candidate pairs (deduplicated across shared readings): **111 094**.

| Значение | Количество |
| --- | --- |
| 0 | 109 852 |
| (0,.5) | 928 |
| [.5,.75) | 205 |
| 1 | 102 |
| [.75,1) | 7 |

109 852 / 111 094 pairs (98.88%) имеют нулевой full-gloss overlap; 109 имеют ≥0.75. Это сигнал для review, не подтверждение variant. Katakana→hiragana меняет counts; normalized-zero counts в section 9 отдельно.

Дополнительный paraphrase proxy: token Jaccard по [a-z0-9]+ из всех CI glosses, без a/an/the/to/of/in/on/and/or/for/with/at/by/as; не semantic similarity.
| Значение | Количество |
| --- | --- |
| token_jaccard_ge_080 | 113 |
| token_jaccard_ge_080_but_exact_gloss_overlap_0 | 3 |

- A: 1307680 **姉弟** [きょうだい, してい] — older sister and younger brother
  - B: 1578240 **兄妹** [きょうだい, けいまい] — older brother and younger sister
  - Token Jaccard=1.0; exact gloss overlap=0.

- A: 2382320 **半加算器** [はんかさんき] — half-adder
  - B: 2382330 **半加算機** [はんかさんき] — half adder
  - Token Jaccard=1.0; exact gloss overlap=0.

- A: 2513560 **菌株** [きんかぶ, きんしゅ] — (bacterial) strain
  - B: 2540470 **菌種** [きんしゅ] — bacterial strain
  - Token Jaccard=1.0; exact gloss overlap=0.

- **こう**: 52 written — 乞う / 亢 / 交 / 侯 / 候 / 公 / 功 / 劫 / 効 / 口 / 喉 / 国府 / 坑 / 好 / 子 / 孝 / 寇 / 工 / 巧 / 幸 / 庚 / 恋う / 抗 / 斯う / 更 / 校 / 江 / 洪 / 港 / 溝 / 爻 / 甲 / 硬 / 稿 / 紅 / 絞 / 綱 / 考 / 腔 / 薨 / 蝗 / 行 / 請う / 講 / 貢 / 鋼 / 長官 / 項 / 香 / 高 / 鵠 / 鸛
- **しょう**: 44 written — 勝 / 匠 / 升 / 商 / 声 / 妾 / 姓 / 将 / 小 / 少 / 少輔 / 庄 / 床 / 性 / 承 / 抄 / 掌 / 昭 / 正 / 漿 / 牀 / 生 / 症 / 相 / 省 / 礁 / 祥 / 称 / 章 / 笙 / 簫 / 背負う / 脊負う / 荘 / 衝 / 証 / 詔 / 請 / 象 / 賞 / 鈔 / 鉦 / 鍾 / 頌
- **かん**: 44 written — 冠 / 刊 / 勘 / 坎 / 奸 / 姦 / 姧 / 完 / 官 / 寒 / 寛 / 巻 / 感 / 棺 / 款 / 歓 / 汗 / 漢 / 澗 / 燗 / 環 / 甲 / 疳 / 癇 / 神 / 稈 / 竿 / 管 / 簡 / 緘 / 缶 / 罐 / 艦 / 観 / 貫 / 鐶 / 鑵 / 長官 / 閑 / 間 / 関 / 韓 / 館 / 骭
- **し**: 43 written — 仕 / 使 / 其 / 刺 / 卮 / 史 / 司 / 嗣 / 四 / 士 / 姉 / 子 / 尸 / 屍 / 巳 / 巵 / 市 / 師 / 支 / 枝 / 梓 / 歯 / 死 / 氏 / 汝 / 私 / 秭 / 糸 / 紙 / 羊蹄 / 肆 / 至 / 視 / 觜 / 詞 / 試 / 詩 / 誌 / 諡 / 謚 / 資 / 食 / 駟
- **そう**: 41 written — 候 / 僧 / 副う / 双 / 叟 / 叢 / 喪 / 壮 / 奏 / 姓 / 宋 / 宗 / 層 / 左右 / 庄 / 惣 / 想 / 操 / 曹 / 曽 / 曾 / 槽 / 沿う / 添う / 然う / 甑 / 疎雨 / 疏雨 / 相 / 笙 / 筝 / 箏 / 総 / 艘 / 草 / 荘 / 葬 / 装 / 走 / 踈雨 / 躁
- **こうこう**: 41 written — 交媾 / 公侯 / 口腔 / 坑口 / 孝行 / 広々 / 広広 / 後攻 / 後項 / 斯う斯う / 晃々 / 晃晃 / 晧々 / 晧晧 / 曠々 / 曠曠 / 洸々 / 洸洸 / 港口 / 煌々 / 煌煌 / 皇考 / 皎々 / 皎皎 / 皓々 / 皓皓 / 硬膏 / 硬鋼 / 膏肓 / 航行 / 遑々 / 遑遑 / 鉱坑 / 鏗々 / 鏗鏗 / 香々 / 香香 / 高工 / 高校 / 高考 / 黄口

Полные entry_id/readings/meanings extreme groups в JSON.

## 7. Near-reading diagnostics

Normalized readings: 158 747. Global distinct normalized reading pairs distance=1: **643 504**. Expansion across different-entry/different-written entities даёт **5 766 950 occurrences**; это не distinct word pairs, поскольку пары могут иметь несколько reading matches.

Distance=1 вычислен для всех readings через single-deletion signatures и exact single-edit verification. Нет полного O(N²). Distance=2 вычислен только по meaning-supported cross-entry candidates (section 9), не по всему словарю; global distance=2 count не заявляется.

| Значение | Количество |
| --- | --- |
| other substitution | 535 649 |
| other insertion/deletion | 82 100 |
| voiced/unvoiced or semi-voiced kana | 10 227 |
| long-vowel-like insertion/deletion (proxy) | 11 367 |
| long-vowel-like substitution (proxy) | 3 113 |
| small-tsu insertion/deletion | 1 048 |

Long-vowel-like — грубый proxy по ー/う/お/い/え; он включает обычные kana edits, не доказывает изменение долготы. Voicing — совпадение базовой NFD kana, включая handakuten.

**other substitution**: かごう ↔ せごう; かじょう ↔ くじょう; あいば ↔ そいば; しんかん ↔ しんたん; もみだす ↔ もれだす; ようてき ↔ ようへき; きゅうけつ ↔ きゅうしつ; がんない ↔ ざんない

**other insertion/deletion**: じづら ↔ もじづら; や ↔ やり; あしゆ ↔ しゆ; じゅうか ↔ にじゅうか; かしく ↔ しく; じんま ↔ じんまく; いがん ↔ でいがん; さう ↔ さどう

**voiced/unvoiced or semi-voiced kana**: にゅうきょ ↔ にゅうぎょ; せいきょう ↔ せいぎょう; きする ↔ ぎする; はたほこ ↔ はたぼこ; しゅうろく ↔ じゅうろく; けたい ↔ けだい; てんえん ↔ でんえん; こきよう ↔ こぎよう

**long-vowel-like insertion/deletion (proxy)**: こんぴゅーたこうがく ↔ こんぴゅーたーこうがく; うちきり ↔ ちきり; いいなか ↔ いなか; きら ↔ きらい; し ↔ しい; てっか ↔ てっかい; やおや ↔ やや; ごうし ↔ ごうしお

**long-vowel-like substitution (proxy)**: しゅううん ↔ しゅうえん; ついく ↔ つうく; うれい ↔ うれえ; うばすてやま ↔ おばすてやま; うら ↔ えら; いんぎょう ↔ おんぎょう; ほうづえ ↔ ほおづえ; けいいん ↔ けいえん

**small-tsu insertion/deletion**: すかんぴん ↔ すっかんぴん; やかい ↔ やっかい; ふいち ↔ ふいっち; さし ↔ さっし; しゅこん ↔ しゅっこん; もす ↔ もっす; じせい ↔ じっせい; それきり ↔ それっきり

### Meaning-supported near-reading examples

- A: 1002410 **お誕生日おめでとうございます** [おたんじょうびおめでとうございます] — happy birthday
  - B: 2231050 **誕生日おめでとうございます** [たんじょうびおめでとうございます] — Happy Birthday
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9286; gloss overlap=1.0; already in same Model C=False.

- A: 1002410 **御誕生日おめでとうございます** [おたんじょうびおめでとうございます] — happy birthday
  - B: 2231050 **誕生日おめでとうございます** [たんじょうびおめでとうございます] — Happy Birthday
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9286; gloss overlap=1.0; already in same Model C=False.

- A: 2850822 **心頭を滅却すれば火もまた涼し** [しんとうをめっきゃくすればひもまたすずし] — if you clear your mind of all worldly thoughts, even fire will feel cool
  - B: 2850985 **心頭滅却すれば火もまた涼し** [しんとうめっきゃくすればひもまたすずし] — if you clear your mind of all worldly thoughts, even fire will feel cool
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9286; gloss overlap=1.0; already in same Model C=False.

- A: 2850822 **心頭を滅却すれば火も亦涼し** [しんとうをめっきゃくすればひもまたすずし] — if you clear your mind of all worldly thoughts, even fire will feel cool
  - B: 2850985 **心頭滅却すれば火も亦涼し** [しんとうめっきゃくすればひもまたすずし] — if you clear your mind of all worldly thoughts, even fire will feel cool
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9231; gloss overlap=1.0; already in same Model C=False.

- A: 2298650 **サービス統合ディジタル網** [サービスとうごうディジタルもう] — ISDN; integrated services digital network
  - B: 2371320 **サービス総合ディジタル網** [サービスそうごうディジタルもう] — ISDN; integrated services digital network
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9167; gloss overlap=1.0; already in same Model C=False.

- A: 2759500 **よいお年をお迎えください** [よいおとしをおむかえください] — have a happy New Year
  - B: 2837759 **よい年をお迎えください** [よいとしをおむかえください] — have a happy New Year
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9167; gloss overlap=1.0; already in same Model C=False.

- A: 2759500 **良いお年をお迎えください** [よいおとしをおむかえください] — have a happy New Year
  - B: 2837759 **良い年をお迎えください** [よいとしをおむかえください] — have a happy New Year
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9167; gloss overlap=1.0; already in same Model C=False.

- A: 2311480 **ディジタルアナログ変換器** [ディジタルアナログへんかんき] — DA converter; DAC; digital-to-analog converter
  - B: 2312350 **デジタルアナログ変換器** [デジタルアナログへんかんき] — DA converter; DAC; digital-to-analog converter
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9167; gloss overlap=1.0; already in same Model C=False.

- A: 2694350 **転んでもただでは起きない** [ころんでもただではおきない] — all's grist that comes to his mill; turn anything to profit; turn everything to good account
  - B: 2694370 **転んでもただは起きない** [ころんでもただはおきない] — all's grist that comes to his mill; turn anything to profit; turn everything to good account
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9167; gloss overlap=1.0; already in same Model C=False.

- A: 2104290 **おごる平家久しからず** [おごるへいけひさしからず] — pride comes before a fall; pride goes before a fall; the proud Heike family does not last long
  - B: 2420190 **おごる平家は久しからず** [おごるへいけはひさしからず] — pride comes before a fall; pride goes before a fall; the proud Heike family does not last long
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9091; gloss overlap=1.0; already in same Model C=False.

- A: 2111700 **事実は小説より奇なり** [じじつはしょうせつよりきなり] — truth is stranger than fiction
  - B: 2111710 **事実は小説よりも奇なり** [じじつはしょうせつよりもきなり] — truth is stranger than fiction
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9091; gloss overlap=1.0; already in same Model C=False.

- A: 2759500 **よいお年をお迎え下さい** [よいおとしをおむかえください] — have a happy New Year
  - B: 2837759 **よい年をお迎え下さい** [よいとしをおむかえください] — have a happy New Year
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9091; gloss overlap=1.0; already in same Model C=False.

- A: 2759500 **良いお年をお迎え下さい** [よいおとしをおむかえください] — have a happy New Year
  - B: 2837759 **良い年をお迎え下さい** [よいとしをおむかえください] — have a happy New Year
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9091; gloss overlap=1.0; already in same Model C=False.

- A: 2183450 **ブルータス、お前もか** [ブルータス、おまえもか] — et tu, Brute?
  - B: 2836754 **ブルータスよ、お前もか** [ブルータスよ、おまえもか] — et tu, Brute?
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9091; gloss overlap=1.0; already in same Model C=False.

- A: 2407990 **下手な考え休むに似たり** [へたなかんがえやすむににたり] — inadequate ideas are worse than none at all; it's hard to tell a poor thinker from a sleeping one; they to whom only bad ideas come might as well be asleep
  - B: 2416750 **下手の考え休むに似たり** [へたのかんがえやすむににたり] — inadequate ideas are worse than none at all; it's hard to tell a poor thinker from a sleeping one; they to whom only bad ideas come might as well be asleep
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9091; gloss overlap=1.0; already in same Model C=False.

- A: 2417810 **弱き者よ汝の名は女なり** [よわきものよなんじのなはおんななり] — frailty, thy name is woman
  - B: 2834263 **弱き者汝の名は女なり** [よわきものなんじのなはおんななり] — frailty, thy name is woman
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9091; gloss overlap=1.0; already in same Model C=False.

- A: 2419120 **転んでもただは起きぬ** [ころんでもただはおきぬ] — all's grist that comes to his mill; turn anything to profit; turn everything to good account
  - B: 2694360 **転んでもただでは起きぬ** [ころんでもただではおきぬ] — all's grist that comes to his mill; turn anything to profit; turn everything to good account
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9091; gloss overlap=1.0; already in same Model C=False.

- A: 2694350 **転んでも只では起きない** [ころんでもただではおきない] — all's grist that comes to his mill; turn anything to profit; turn everything to good account
  - B: 2694370 **転んでも只は起きない** [ころんでもただはおきない] — all's grist that comes to his mill; turn anything to profit; turn everything to good account
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9091; gloss overlap=1.0; already in same Model C=False.

- A: 2419730 **便りのないのはよい便り** [たよりのないのはよいたより] — no news is good news
  - B: 2839180 **便りがないのはよい便り** [たよりがないのはよいたより] — no news is good news
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9091; gloss overlap=1.0; already in same Model C=False.

- A: 2419730 **便りの無いのは良い便り** [たよりのないのはよいたより] — no news is good news
  - B: 2839180 **便りが無いのは良い便り** [たよりがないのはよいたより] — no news is good news
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9091; gloss overlap=1.0; already in same Model C=False.

## 8. Written-variation diagnostics

| Значение | Количество |
| --- | --- |
| kana spelling variation, same kanji sequence | 4 535 |
| kana replaced by kanji (proxy) | 22 379 |
| other/unclassified | 3 162 |
| mixed kanji/kana changes | 14 986 |
| one kanji replaced by another | 19 129 |
| exact-position kanji/kana substitution | 14 373 |
| kanji replaced by kana (proxy) | 485 |
| added/removed kana (okurigana proxy) | 11 172 |

Counts — все неупорядоченные пары written внутри imported same-entry (90 221 pairs), не entries. Categories mutually exclusive и намеренно грубые. Kana insertion при одинаковой kanji sequence — proxy okurigana; вставленная kana может быть не okurigana. Directed kanji↔kana labels ориентированы по lexicographic pair order, а не по историческому изменению. Замены по kanji subset не гарантируют корректный alignment. Нельзя использовать эти labels для production merge.

**kana spelling variation, same kanji sequence**

- 1000200: あうんの呼吸 ↔ あ・うんの呼吸
- 1000390: あっという間に ↔ あっとゆう間に
- 1000390: あっという間に ↔ アッという間に
- 1000390: あっという間に ↔ アッとゆう間に
- 1000390: あっとゆう間に ↔ アッという間に
- 1000390: あっとゆう間に ↔ アッとゆう間に
- 1000390: あっと言う間に ↔ アッと言う間に
- 1000390: アッという間に ↔ アッとゆう間に

**kana replaced by kanji (proxy)**

- 1000200: あうんの呼吸 ↔ 阿吽の呼吸
- 1000390: あっという間に ↔ アッと言う間に
- 1000390: あっとゆう間に ↔ アッと言う間に
- 1000810: いじり回す ↔ 弄り回す
- 1000810: 弄りまわす ↔ 弄り回す
- 1001650: おかげ様 ↔ お蔭様
- 1001650: おかげ様 ↔ お陰様
- 1001650: おかげ様 ↔ 御蔭様

**other/unclassified**

- 1000200: あ・うんの呼吸 ↔ 阿吽の呼吸
- 1000620: 否々 ↔ 否否
- 1003560: 刻々 ↔ 刻刻
- 1003560: 刻々 ↔ 段段
- 1003560: 刻刻 ↔ 段々
- 1003560: 段々 ↔ 段段
- 1005450: 悄々 ↔ 悄悄
- 1005450: 悄々 ↔ 萎萎

**mixed kanji/kana changes**

- 1000225: 偸閑 ↔ 明白
- 1000225: 偸閑 ↔ 白地
- 1000225: 明白 ↔ 白地
- 1000260: 悪どい ↔ 灰汁どい
- 1000490: 天魚 ↔ 甘子
- 1000490: 天魚 ↔ 雨子
- 1000810: いじり回す ↔ 弄りまわす
- 1001130: 海鷂魚 ↔ 鱏

**one kanji replaced by another**

- 1000300: 遇う ↔ 配う
- 1000320: 彼処 ↔ 彼所
- 1000490: 甘子 ↔ 雨子
- 1000610: いい年をして ↔ いい歳をして
- 1000910: 厭に ↔ 嫌に
- 1001130: 鱏 ↔ 鱝
- 1001490: お呪い ↔ お咒い
- 1001640: お蔭 ↔ お陰

**exact-position kanji/kana substitution**

- 1000390: あっという間に ↔ あっと言う間に
- 1000390: あっとゆう間に ↔ あっと言う間に
- 1000390: アッという間に ↔ アッと言う間に
- 1000390: アッとゆう間に ↔ アッと言う間に
- 1000440: あの人 ↔ 彼の人
- 1000450: あの方 ↔ 彼の方
- 1000650: いかなる場合でも ↔ 如何なる場合でも
- 1001180: お出でになる ↔ 御出でになる

**kanji replaced by kana (proxy)**

- 1000390: あっと言う間に ↔ アッという間に
- 1000390: あっと言う間に ↔ アッとゆう間に
- 1001510: お襁褓気触れ ↔ オムツ気触れ
- 1002360: お待ちどう様 ↔ お待ちどおさま
- 1003820: 山梔子 ↔ 梔
- 1003820: 山梔子 ↔ 梔子
- 1008270: 梃子摺る ↔ 梃摺る
- 1010190: 含羞む ↔ 羞む

**added/removed kana (okurigana proxy)**

- 1000580: 彼 ↔ 彼れ
- 1001290: 悍しい ↔ 悍ましい
- 1001720: お願いしまあす ↔ お願いします
- 1001870: お見舞 ↔ お見舞い
- 1001870: 御見舞 ↔ 御見舞い
- 1001890: お好み焼 ↔ お好み焼き
- 1001890: 御好み焼 ↔ 御好み焼き
- 1001970: お仕舞 ↔ お仕舞い

## 9. Cross-entry candidate diagnostics

Все counts — пары imported (entry,written), different written и different entry. Reading distance — minimum по normalized reading sets. Written similarity = `1 - Levenshtein/max(lengths)`. Meaning overlap — полные CI gloss sets. Это несколько диагностических thresholds, не production algorithm.

| Threshold | distance 0 | distance 1 | distance 2 |
| --- | --- | --- | --- |
| Meaning overlap ≥0.50 | 323 | 11 130 | 23 608 |
| Meaning overlap ≥0.75 | 113 | 6 897 | 9 418 |
| Overlap ≥0.75 + written similarity ≥0.50 | 82 | 5 247 | 6 144 |
| Overlap ≥0.75 + written distance ≤1 | 64 | 3 236 | 3 972 |
| Overlap =1 | 106 | 6 136 | 8 636 |

Shared-exact-gloss inverted index covered 1 359 202 cross-entry/different-written pairs. Counts distance=2 исчерпывающие **только внутри overlap≥0.50 scope**. Вне Model C остаются 97 normalized exact-reading/high-overlap pairs и 6 648 near-distance-1/high-overlap pairs. Не все — variants: технические названия, школы, похожие термины и обычные синонимы дают совпадающие glosses.

### 50 candidates (diverse entry pairs, не certified variants)

- A: 1598870 **飛びきり** [とびきり] — best; by far; exceptionally; extra-fine; extraordinarily; jumping and slashing (e.g. at an enemy); superfine; unequalled
  - B: 2833447 **とびっ切り** [とびっきり] — best; by far; exceptionally; extra-fine; extraordinarily; superfine; unequalled
  - Signals: normalized reading distance=1; written distance=3; written similarity=0.4; gloss overlap=0.875; already in same Model C=False.

- A: 1002410 **お誕生日おめでとうございます** [おたんじょうびおめでとうございます] — happy birthday
  - B: 2231050 **誕生日おめでとうございます** [たんじょうびおめでとうございます] — Happy Birthday
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9286; gloss overlap=1.0; already in same Model C=False.

- A: 2850822 **心頭を滅却すれば火もまた涼し** [しんとうをめっきゃくすればひもまたすずし] — if you clear your mind of all worldly thoughts, even fire will feel cool
  - B: 2850985 **心頭滅却すれば火もまた涼し** [しんとうめっきゃくすればひもまたすずし] — if you clear your mind of all worldly thoughts, even fire will feel cool
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9286; gloss overlap=1.0; already in same Model C=False.

- A: 2298650 **サービス統合ディジタル網** [サービスとうごうディジタルもう] — ISDN; integrated services digital network
  - B: 2371320 **サービス総合ディジタル網** [サービスそうごうディジタルもう] — ISDN; integrated services digital network
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9167; gloss overlap=1.0; already in same Model C=False.

- A: 2311480 **ディジタルアナログ変換器** [ディジタルアナログへんかんき] — DA converter; DAC; digital-to-analog converter
  - B: 2312350 **デジタルアナログ変換器** [デジタルアナログへんかんき] — DA converter; DAC; digital-to-analog converter
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9167; gloss overlap=1.0; already in same Model C=False.

- A: 2694350 **転んでもただでは起きない** [ころんでもただではおきない] — all's grist that comes to his mill; turn anything to profit; turn everything to good account
  - B: 2694370 **転んでもただは起きない** [ころんでもただはおきない] — all's grist that comes to his mill; turn anything to profit; turn everything to good account
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9167; gloss overlap=1.0; already in same Model C=False.

- A: 2759500 **よいお年をお迎えください** [よいおとしをおむかえください] — have a happy New Year
  - B: 2837759 **よい年をお迎えください** [よいとしをおむかえください] — have a happy New Year
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9167; gloss overlap=1.0; already in same Model C=False.

- A: 2104290 **おごる平家久しからず** [おごるへいけひさしからず] — pride comes before a fall; pride goes before a fall; the proud Heike family does not last long
  - B: 2420190 **おごる平家は久しからず** [おごるへいけはひさしからず] — pride comes before a fall; pride goes before a fall; the proud Heike family does not last long
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9091; gloss overlap=1.0; already in same Model C=False.

- A: 2111700 **事実は小説より奇なり** [じじつはしょうせつよりきなり] — truth is stranger than fiction
  - B: 2111710 **事実は小説よりも奇なり** [じじつはしょうせつよりもきなり] — truth is stranger than fiction
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9091; gloss overlap=1.0; already in same Model C=False.

- A: 2183450 **ブルータス、お前もか** [ブルータス、おまえもか] — et tu, Brute?
  - B: 2836754 **ブルータスよ、お前もか** [ブルータスよ、おまえもか] — et tu, Brute?
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9091; gloss overlap=1.0; already in same Model C=False.

- A: 2407990 **下手な考え休むに似たり** [へたなかんがえやすむににたり] — inadequate ideas are worse than none at all; it's hard to tell a poor thinker from a sleeping one; they to whom only bad ideas come might as well be asleep
  - B: 2416750 **下手の考え休むに似たり** [へたのかんがえやすむににたり] — inadequate ideas are worse than none at all; it's hard to tell a poor thinker from a sleeping one; they to whom only bad ideas come might as well be asleep
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9091; gloss overlap=1.0; already in same Model C=False.

- A: 2417810 **弱き者よ汝の名は女なり** [よわきものよなんじのなはおんななり] — frailty, thy name is woman
  - B: 2834263 **弱き者汝の名は女なり** [よわきものなんじのなはおんななり] — frailty, thy name is woman
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9091; gloss overlap=1.0; already in same Model C=False.

- A: 2419120 **転んでもただは起きぬ** [ころんでもただはおきぬ] — all's grist that comes to his mill; turn anything to profit; turn everything to good account
  - B: 2694360 **転んでもただでは起きぬ** [ころんでもただではおきぬ] — all's grist that comes to his mill; turn anything to profit; turn everything to good account
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9091; gloss overlap=1.0; already in same Model C=False.

- A: 2419730 **便りのないのはよい便り** [たよりのないのはよいたより] — no news is good news
  - B: 2839180 **便りがないのはよい便り** [たよりがないのはよいたより] — no news is good news
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9091; gloss overlap=1.0; already in same Model C=False.

- A: 2858678 **自分のことは棚にあげる** [じぶんのことはたなにあげる] — to act hypocritically; to ignore one's own shortcomings; to shut one's eyes to one's own faults
  - B: 2858729 **自分のことを棚にあげる** [じぶんのことをたなにあげる] — to act hypocritically; to ignore one's own shortcomings; to shut one's eyes to one's own faults
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9091; gloss overlap=1.0; already in same Model C=False.

- A: 2102410 **置いてきぼりを食う** [おいてきぼりをくう] — to be left behind
  - B: 2140140 **置いてきぼりを食らう** [おいてきぼりをくらう] — to be left behind
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9; gloss overlap=1.0; already in same Model C=False.

- A: 2341790 **引き込みケーブル** [ひきこみケーブル] — drop cable
  - B: 2341800 **引込みケーブル** [ひきこみケーブル] — drop cable
  - Signals: normalized reading distance=0; written distance=1; written similarity=0.875; gloss overlap=1.0; already in same Model C=False.

- A: 2378650 **動的資源割り振り** [どうてきしげんわりふり] — dynamic resource allocation
  - B: 2378660 **動的資源割振り** [どうてきしげんわりふり] — dynamic resource allocation
  - Signals: normalized reading distance=0; written distance=1; written similarity=0.875; gloss overlap=1.0; already in same Model C=False.

- A: 2362470 **手続き型言語** [てつづきがたげんご] — procedural language
  - B: 2362480 **手続き形言語** [てつづきがたげんご] — procedural language
  - Signals: normalized reading distance=0; written distance=1; written similarity=0.8333; gloss overlap=1.0; already in same Model C=False.

- A: 5339502 **七生養護学校** [ななおようごがっこう] — Nanao Special Needs School (older name style)
  - B: 5339774 **七尾養護学校** [ななおようごがっこう] — Nanao Special Needs School (older name style)
  - Signals: normalized reading distance=0; written distance=1; written similarity=0.8333; gloss overlap=1.0; already in same Model C=False.

- A: 5365057 **純心女子短大** [じゅんしんじょしたんだい] — Junshin Women's Junior College
  - B: 5365060 **純真女子短大** [じゅんしんじょしたんだい] — Junshin Women's Junior College
  - Signals: normalized reading distance=0; written distance=1; written similarity=0.8333; gloss overlap=1.0; already in same Model C=False.

- A: 5626773 **文京女子大学** [ぶんきょうじょしだいがく] — Bunkyō Women's University
  - B: 5626793 **文教女子大学** [ぶんきょうじょしだいがく] — Bunkyō Women's University
  - Signals: normalized reading distance=0; written distance=1; written similarity=0.8333; gloss overlap=1.0; already in same Model C=False.

- A: 1294040 **最小自乗法** [さいしょうじじょうほう] — least-squares method
  - B: 1294060 **最小二乗法** [さいしょうじじょうほう, さいしょうにじょうほう] — least-squares method
  - Signals: normalized reading distance=0; written distance=1; written similarity=0.8; gloss overlap=1.0; already in same Model C=False.

- A: 1368970 **人まかせ** [ひとまかせ] — leaving (it) to others
  - B: 2211820 **他人まかせ** [たにんまかせ, ひとまかせ] — leaving (it) to others
  - Signals: normalized reading distance=0; written distance=1; written similarity=0.8; gloss overlap=1.0; already in same Model C=False.

- A: 1408770 **打ち滅ぼす** [うちほろぼす] — to destroy
  - B: 1697620 **討ち滅ぼす** [うちほろぼす] — to destroy
  - Signals: normalized reading distance=0; written distance=1; written similarity=0.8; gloss overlap=1.0; already in same Model C=False.

- A: 1423900 **中距離競走** [ちゅうきょりきょうそう] — middle-distance race
  - B: 1695860 **中距離競争** [ちゅうきょりきょうそう] — middle-distance race
  - Signals: normalized reading distance=0; written distance=1; written similarity=0.8; gloss overlap=1.0; already in same Model C=False.

- A: 1697380 **卸売り価格** [おろしうりかかく] — wholesale price
  - B: 1973970 **卸売価格** [おろしうりかかく] — wholesale price
  - Signals: normalized reading distance=0; written distance=1; written similarity=0.8; gloss overlap=1.0; already in same Model C=False.

- A: 1724360 **火災報知機** [かさいほうちき] — fire alarm
  - B: 1931250 **火災報知器** [かさいほうちき] — fire alarm
  - Signals: normalized reading distance=0; written distance=1; written similarity=0.8; gloss overlap=1.0; already in same Model C=False.

- A: 1990340 **起業家精神** [きぎょうかせいしん] — entrepreneurial spirit; entrepreneurship
  - B: 2858984 **企業家精神** [きぎょうかせいしん] — entrepreneurial spirit; entrepreneurship
  - Signals: normalized reading distance=0; written distance=1; written similarity=0.8; gloss overlap=1.0; already in same Model C=False.

- A: 2348100 **関数型言語** [かんすうがたげんご] — functional language
  - B: 2348120 **関数形言語** [かんすうがたげんご] — functional language
  - Signals: normalized reading distance=0; written distance=1; written similarity=0.8; gloss overlap=1.0; already in same Model C=False.

- A: 2369610 **宣言型言語** [せんげんがたげんご] — declarative language
  - B: 2369620 **宣言形言語** [せんげんがたげんご] — declarative language
  - Signals: normalized reading distance=0; written distance=1; written similarity=0.8; gloss overlap=1.0; already in same Model C=False.

- A: 2418250 **人はパンのみにて生くる者に非ず** [ひとはパンのみにていくるものにあらず] — man shall not live by bread alone
  - B: 2868513 **人はパンのみにて生くるに非ず** [ひとはパンのみにていくるにあらず] — man shall not live by bread alone
  - Signals: normalized reading distance=2; written distance=1; written similarity=0.9333; gloss overlap=1.0; already in same Model C=False.

- A: 2158240 **若いときの苦労は買うてもせよ** [わかいときのくろうはこうてもせよ] — heavy work in youth is quiet rest in old age; spare no effort while you are young
  - B: 2827732 **若いときの苦労は買ってもせよ** [わかいときのくろうはかってもせよ] — heavy work in youth is quiet rest in old age; spare no effort while you are young
  - Signals: normalized reading distance=2; written distance=1; written similarity=0.9286; gloss overlap=1.0; already in same Model C=False.

- A: 2395080 **過ぎたるは及ばざるが如し** [すぎたるはおよばざるがごとし] — less is more; the last drop makes the cup run over; too much of a good thing
  - B: 2395090 **過ぎたるは猶及ばざるが如し** [すぎたるはなおおよばざるがごとし] — less is more; the last drop makes the cup run over; too much of a good thing
  - Signals: normalized reading distance=2; written distance=1; written similarity=0.9231; gloss overlap=1.0; already in same Model C=False.

- A: 2609850 **連合国軍最高司令官総司令部** [れんごうこくぐんさいこうしれいかんそうしれいぶ] — GHQ; General Headquarters, Supreme Commander for the Allied Powers (1945-1952)
  - B: 2787860 **連合国最高司令官総司令部** [れんごうこくさいこうしれいかんそうしれいぶ] — GHQ; General Headquarters, Supreme Commander for the Allied Powers (1945-1952)
  - Signals: normalized reading distance=2; written distance=1; written similarity=0.9231; gloss overlap=1.0; already in same Model C=False.

- A: 1986640 **コンピューター断層撮影** [コンピューターだんそうさつえい] — CT; computed tomography; computerized tomography
  - B: 2220000 **コンピューター断層撮影法** [コンピューターだんそうさつえいほう] — CT; computed tomography; computerized tomography
  - Signals: normalized reading distance=2; written distance=1; written similarity=0.9167; gloss overlap=1.0; already in same Model C=False.

- A: 2416740 **遠くの親類より近くの他人** [とおくのしんるいよりちかくのたにん] — a neighbour is better than a relative living far
  - B: 2844711 **遠くの親戚より近くの他人** [とおくのしんせきよりちかくのたにん] — a neighbour is better than a relative living far
  - Signals: normalized reading distance=2; written distance=1; written similarity=0.9167; gloss overlap=1.0; already in same Model C=False.

- A: 2706450 **ロボット検索エンジン** [ロボットけんさくエンジン] — spider; web crawler
  - B: 2706460 **ロボット型検索エンジン** [ロボットがたけんさくエンジン] — spider; web crawler
  - Signals: normalized reading distance=2; written distance=1; written similarity=0.9091; gloss overlap=1.0; already in same Model C=False.

- A: 2082600 **部分肺静脈還流異常** [ぶぶんはいじょうみゃくかんりゅういじょう] — PAPVC; PAPVD; PAPVR; partial anomalous pulmonary venous connection; partial anomalous pulmonary venous drainage; partial anomalous pulmonary venous return
  - B: 2082610 **部分的肺静脈還流異常** [ぶぶんてきはいじょうみゃくかんりゅういじょう] — PAPVC; PAPVD; PAPVR; partial anomalous pulmonary venous connection; partial anomalous pulmonary venous drainage; partial anomalous pulmonary venous return
  - Signals: normalized reading distance=2; written distance=1; written similarity=0.9; gloss overlap=1.0; already in same Model C=False.

- A: 2133750 **宜しくお願いします** [よろしくおねがいします] — I look forward to working with you; please do; please help me; please remember me; please take care of; please treat me well
  - B: 2133760 **宜しくお願い致します** [よろしくおねがいいたします] — I look forward to working with you; please do; please help me; please remember me; please take care of; please treat me well
  - Signals: normalized reading distance=2; written distance=1; written similarity=0.9; gloss overlap=1.0; already in same Model C=False.

- A: 2283630 **インタープリタ型言語** [インタープリタがたげんご] — interpreted language
  - B: 2862263 **インタープリタ言語** [インタープリタげんご] — interpreted language
  - Signals: normalized reading distance=2; written distance=1; written similarity=0.9; gloss overlap=1.0; already in same Model C=False.

- A: 2358850 **使用パラメータ制御** [しようパラメータせいぎょ] — UPC; user parameter control
  - B: 2359000 **使用率パラメータ制御** [しようりつパラメータせいぎょ] — UPC; user parameter control
  - Signals: normalized reading distance=2; written distance=1; written similarity=0.9; gloss overlap=1.0; already in same Model C=False.

- A: 2595290 **注意欠如・多動性障害** [ちゅういけつじょたどうせいしょうがい] — ADHD; attention deficit hyperactivity disorder
  - B: 2864228 **注意欠如・多動障害** [ちゅういけつじょたどうしょうがい] — ADHD; attention deficit hyperactivity disorder
  - Signals: normalized reading distance=2; written distance=1; written similarity=0.9; gloss overlap=1.0; already in same Model C=False.

- A: 2595300 **注意欠陥・多動性障害** [ちゅういけっかんたどうせいしょうがい] — ADHD; attention deficit hyperactivity disorder
  - B: 2864195 **注意欠陥・多動障害** [ちゅういけっかんたどうしょうがい] — ADHD; attention deficit hyperactivity disorder
  - Signals: normalized reading distance=2; written distance=1; written similarity=0.9; gloss overlap=1.0; already in same Model C=False.

- A: 2849931 **あさっての方を向く** [あさってのほうをむく] — to face the wrong way; to go in the wrong direction
  - B: 2862291 **あさっての方向を向く** [あさってのほうこうをむく] — to face the wrong way; to go in the wrong direction
  - Signals: normalized reading distance=2; written distance=1; written similarity=0.9; gloss overlap=1.0; already in same Model C=False.

- A: 2862583 **外人コンプレックス** [がいじんコンプレックス] — (supposed) Japanese feeling of inferiority towards foreigners (particularly Westerners); foreigner complex
  - B: 2868646 **外国人コンプレックス** [がいこくじんコンプレックス] — (supposed) Japanese feeling of inferiority towards foreigners (particularly Westerners); foreigner complex
  - Signals: normalized reading distance=2; written distance=1; written similarity=0.9; gloss overlap=1.0; already in same Model C=False.

- A: 2137710 **石にかじりついても** [いしにかじりついても] — come hell or high water
  - B: 2258590 **石にかじりついてでも** [いしにかじりついてでも] — come hell or high water
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9; gloss overlap=1.0; already in same Model C=False.

- A: 2168370 **目くそ鼻くそを笑う** [めくそはなくそをわらう] — the goop in the eye laughing at the snot in the nose; the pot calling the kettle black
  - B: 2852791 **目くそが鼻くそを笑う** [めくそがはなくそをわらう] — the goop in the eye laughing at the snot in the nose; the pot calling the kettle black
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9; gloss overlap=1.0; already in same Model C=False.

- A: 2171910 **去る者は日々にうとし** [さるものはひびにうとし] — friendships fade with distance; out of sight, out of mind; with time we forget those who have died
  - B: 2416940 **去る者日々にうとし** [さるものひびにうとし] — friendships fade with distance; out of sight, out of mind; with time we forget those who have died
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9; gloss overlap=1.0; already in same Model C=False.

- A: 2358340 **索引順アクセス方式** [さくいんじゅんアクセスほうしき] — ISAM; Indexed Sequential Access Method
  - B: 2358350 **索引順次アクセス方式** [さくいんじゅんじアクセスほうしき] — ISAM; Indexed Sequential Access Method
  - Signals: normalized reading distance=1; written distance=1; written similarity=0.9; gloss overlap=1.0; already in same Model C=False.

### 20 false-positive-looking homophones (overlap=0)

- A: 2188750 **羊蹄** [ぎしぎし, し, しのね, ようてい] — Japanese dock (Rumex japonicus)
  - B: 2842987 **嗣** [し] — succession; successor
  - Exact readings: し; full-gloss overlap=0.

- A: 1440810 **副う** [そう] — to accompany; to associate with (someone); to be added; to comply with; to go with; to live up to; to marry; to meet (wishes, expectations, etc.); to mix with; to satisfy; to stay by one's side; to wed
  - B: 2137720 **然う** [そう] — in that way; so; so?; such; that's the case; the same; thus
  - Exact readings: そう; full-gloss overlap=0.

- A: 1398670 **双** [そう] — pair
  - B: 1399590 **想** [そう] — conception; idea; samjna (perception); thought
  - Exact readings: そう; full-gloss overlap=0.

- A: 1279350 **更始** [こうし] — reform; renewal
  - B: 2765290 **考試** [こうし] — exam; test
  - Exact readings: こうし; full-gloss overlap=0.

- A: 2253490 **綺** [かにはた, かんはた, かんばた, き] — thin twilled silk fabric
  - B: 2813080 **稀** [き] — dilute; rare
  - Exact readings: き; full-gloss overlap=0.

- A: 1198170 **会** [かい] — assembly; association; athletic meet; club; conference; gathering; meeting; party; society
  - B: 2754480 **下衣** [かい, したごろも] — lower garment (e.g. trousers); undergarment; underwear
  - Exact readings: かい; full-gloss overlap=0.

- A: 1199330 **回** [かい] — Hui (people); Islam; a time; an instance; chapter; counter for occurrences; episode; game; inning (baseball); instalment; round
  - B: 1280250 **甲斐** [かい] — avail; benefit; effect; result (that makes an act worthwhile); use; value; worth (in doing something)
  - Exact readings: かい; full-gloss overlap=0.

- A: 1277670 **好尚** [こうしょう] — fancy; fashion; taste
  - B: 1875160 **光照** [こうしょう] — shining
  - Exact readings: こうしょう; full-gloss overlap=0.

- A: 1693050 **五〇** [い, いそ, ごじゅう] — 50; fifty
  - B: 2672350 **夷** [い] — barbarian
  - Exact readings: い; full-gloss overlap=0.

- A: 1618960 **潜行** [せんこう] — going into hiding; going underground; travelling in disguise; travelling incognito; underwater navigation
  - B: 2108600 **先公** [せんこう, センコウ, センコー] — late lord; previous lord; previous ruler; teach; teacher
  - Exact readings: せんこう; full-gloss overlap=0.

- A: 1275490 **厚生** [こうせい] — (former) Ministry of Health and Welfare; public welfare; social welfare; welfare
  - B: 1593520 **強勢** [こうせい, ごうせい] — big (e.g. storm); extremely; grand; great; greatly; large; lavish; luxurious; sumptuous; very
  - Exact readings: こうせい; full-gloss overlap=0.

- A: 1571860 **虱** [しし, しらみ] — lice; louse (esp. a sucking louse)
  - B: 2165030 **士師** [しし] — judge (leader of ancient Israel); judge (of ancient China)
  - Exact readings: しし; full-gloss overlap=0.

- A: 1572560 **訌争** [こうそう] — internal discord; internal dissension
  - B: 1685600 **航走** [こうそう] — sailing
  - Exact readings: こうそう; full-gloss overlap=0.

- A: 1596000 **正々** [せいせい] — accurate; exact; neat; punctual
  - B: 2855523 **世世** [せいせい, せせ, せぜ] — many generations
  - Exact readings: せいせい; full-gloss overlap=0.

- A: 1831960 **然う斯う** [そうこう] — one thing or another; this and that
  - B: 2849986 **桑港** [そうこう, サンフランシスコ] — San Francisco
  - Exact readings: そうこう; full-gloss overlap=0.

- A: 1872820 **郭** [かく, くるわ] — area enclosed by earthwork; district; enclosure; quarter; red-light district
  - B: 2076890 **斯く** [かく] — in this way; like this; such; thus
  - Exact readings: かく; full-gloss overlap=0.

- A: 2034230 **高塔** [こうとう] — high tower
  - B: 2838491 **恒等** [こうとう] — identity
  - Exact readings: こうとう; full-gloss overlap=0.

- A: 1219500 **喜々** [きき] — gleeful; joyful; merry
  - B: 1868740 **輝々** [きき] — brilliant (of light); shining
  - Exact readings: きき; full-gloss overlap=0.

- A: 2150000 **四更** [しこう] — fourth watch of the night (approx. 1am to 3am)
  - B: 2853020 **紙工** [しこう] — paper processing
  - Exact readings: しこう; full-gloss overlap=0.

- A: 1374620 **制** [せい] — control; establishment; government; holding back; imperial command; laws; organisation; organization; regulation; restraint; suppression; system
  - B: 1375020 **勢** [せい] — (number of) troops; army; energy; military strength; power; strength
  - Exact readings: せい; full-gloss overlap=0.

Среди high-overlap кандидатов особенно требуют проверки пары названий разных школ/университетов: 七生養護学校/七尾養護学校; 純心女子短大/純真女子短大; 文京女子大学/文教女子大学. Совпадение English rendering не доказывает идентичность учреждения.

## 10. Hypothetical Model A/B/C counts

| Model | Identity | Entities | Reduction vs A |
| --- | --- | --- | --- |
| A | (entry_id,written) | 231 241 | — |
| B | exact written | 226 836 | 1.90% |
| C | connected components: same-entry OR same-written | 171 585 | 25.80% |

C связывает nodes (entry_id,written) через same-entry и exact-written без reading/gloss фильтра. Все links транзитивны. Это diagnostic grouping, не утверждение lexical equivalence.

Distribution distinct written per C group:
| Значение | Количество |
| --- | --- |
| 1 | 136 856 |
| 2 | 23 079 |
| 3 | 6 858 |
| 4 | 2 832 |
| 5 | 964 |
| 6 | 554 |
| 7 | 210 |
| 8 | 86 |
| 9 | 48 |
| 10 | 44 |
| 11 | 15 |
| 12 | 17 |
| 13 | 6 |
| 14 | 7 |
| 15 | 1 |
| 16 | 2 |
| 17 | 1 |
| 18 | 2 |
| 19 | 1 |
| 34 | 1 |
| 48 | 1 |

Distribution distinct source entry_ids per C group:
| Значение | Количество |
| --- | --- |
| 1 | 168 746 |
| 2 | 2 273 |
| 3 | 356 |
| 4 | 103 |
| 5 | 44 |
| 6 | 20 |
| 7 | 10 |
| 8 | 8 |
| 9 | 4 |
| 10 | 6 |
| 11 | 2 |
| 12 | 1 |
| 13 | 2 |
| 14 | 2 |
| 16 | 1 |
| 17 | 1 |
| 22 | 1 |
| 25 | 1 |
| 29 | 1 |
| 30 | 1 |
| 40 | 1 |
| 61 | 1 |

### 30 biggest/interesting C groups, all written/entries/readings/glosses

- **48 written / 61 source entries / 77 readings / 178 glosses**
  - Written: おとこの娘 / おのれ等 / 俺 / 俺ら / 俺等 / 側 / 側ら / 側女 / 側妻 / 傍 / 傍ら / 儞 / 其 / 其れ / 其方 / 具 / 助 / 夫 / 女 / 妻 / 己 / 己ら / 己れ / 己れら / 己れ等 / 己等 / 掖 / 旁 / 旁々 / 旁旁 / 汝 / 汝等 / 然 / 爾 / 牝 / 牡 / 牸 / 男 / 男のコ / 男の娘 / 男の子 / 端 / 端た / 脇 / 腋 / 良人 / 雄 / 雌
  - Entries: 1006780, 1006970, 1244950, 1266890, 1294330, 1312880, 1344360, 1344370, 1344930, 1344940, 1394690, 1403830, 1419990, 1420010, 1496480, 1562530, 1567860, 1576870, 1576880, 1578530, 1581310, 1581610, 1589190, 1590370, 1590440, 1631650, 1634450, 2015140, 2080360, 2084820, 2173530, 2174410, 2174420, 2174430, 2174450, 2174460, 2174490, 2174500, 2174510, 2590770, 2735810, 2742850, 2742870, 2742880, 2742890, 2746040, 2746070, 2754880, 2764560, 2826522, 2836662, 2837821, 2840654, 2843131, 2843485, 2852371, 2860163, 2862416, 2867582, 2869001, 2870787
  - Readings: い, いまし, うぬ, うぬら, うら, お, おいら, おす, おっこ, おっと, おとこ, おとこのこ, おどれ, おぬ, おの, おのこ, おのれ, おのれら, おみな, おら, おらあ, おり, おれ, おれら, おん, おんどれ, おんな, かたがた, かたわら, かわ, がわ, き, ぐ, さ, さい, し, しか, しゃ, じょ, すけ, ぜん, そ, そく, そちら, そっち, そなた, そば, そばめ, それ, たん, だん, つくり, つちのと, つま, な, なむたち, なむだち, なむち, なれ, なん, なんじ, はし, はした, はじ, はた, はな, ふ, まし, みまし, め, めす, めん, ゆう, りょうじん, りょうにん, わいら, わき
  - Deduplicated English glosses: (place) close by; (place) near by; (place) right next to; (someone's) woman; (watch) case; (working) man; -like; 6th in rank; Chinese "Girl" constellation (one of the 28 mansions); I; accompaniment; armpit; as soon as; as such; aside; assistance; assistant; at the same time; away; babe; baby boy; baron; beginning; beside; besides; best (of); blockhead!; bloke; boy; boyfriend; brave; broad; by; by oneself (itself, etc.); bystanders; chap; chick; close female servant; close to; concubine; counter for sets of armor, utensils, furniture, etc.; daughter; dear; deuteragonist; edge; end; end (e.g. of street); excellence; extremity; fellow; female; female (animal, plant); female lover; femboy; feminine noun; first; first principle of the Eight Principles of Yong; flank; for haori: at least 7.27 m in length; for kimonos: at least 10 m in length; for other clothes: at least 6.06 m in length; fraction; gable; gable wall; garnish (esp. one served with sashimi); girl; girlfriend; good person; great person; greater; greatness; guy; he; help; helper; heroic; honey; husband; in addition to; in that way; incidentally; ingredients (added to soup, rice, etc.); interjection expressing anger or chagrin; it; kept woman; laborer; larger (of the two); leading figure; least; like that; low class female servant; male; male (animal, plant); male lover; man; manliness; manly; manly honor; manly honour; manly reputation; margin; masculine noun; me; means; mistress; my dear; myself; near; nearby; neighbourhood (neighborhood); odd bit; odd money; odd sum; odds and ends; off-topic; off-track; one's wife; oneself; oneself (itself, etc.); onlookers; origin; others; out of the way; outsiders; part; point; proximity; rescue; right after; right-hand radical of a character; scrap; second verse (in a linked series of poems); she; side; side (of something, or taking someone's side); side (remark); sixth sign of the Chinese calendar; small change; smaller (of the two); so; son; start; supporting role; that; that (one); that direction; that person; that point (in time); that time; that way; the side (of something); themself; then; there; third person; tiny dash or speck; tip; to the side; tool; trimmings; uh-huh; under one's arm; us; variable measure of fabric (28.8 cm in width); vicinity; we; weaker; while (doing); wife; woman; ye; yeah; you; you (plural); young male with a feminine appearance (esp. one who crossdresses and has a feminine gender expression); young man; your company; your family

- **34 written / 40 source entries / 44 readings / 213 glosses**
  - Written: 体 / 例 / 例し / 卵 / 容 / 形 / 態 / 方 / 様 / 殻 / 滸 / 状 / 玉子 / 畔 / 畝 / 畦 / 稃 / 貌 / 身 / 身体 / 身體 / 躯 / 躰 / 軀 / 辺 / 辺り / 顏 / 顔 / 顔かたち / 顔容 / 顔形 / 顔貌 / 骸 / 體
  - Entries: 1205740, 1217730, 1217800, 1250220, 1250980, 1356690, 1365520, 1374420, 1374430, 1409140, 1409150, 1409160, 1410750, 1512070, 1512080, 1516925, 1516930, 1545790, 1549140, 1585230, 1605840, 1956130, 2068070, 2097910, 2101970, 2138450, 2210770, 2221310, 2253290, 2254010, 2254960, 2396240, 2427890, 2647520, 2647530, 2713240, 2830705, 2842190, 2842191, 2868628
  - Readings: あぜ, あたり, うね, かい, かいご, かお, かおかたち, かく, かた, かたち, から, からだ, かんばせ, がお, がた, がら, がんぼう, がんよう, く, くろ, けい, さま, ざま, しんたい, しんだい, しんてい, じょう, せ, たい, たまご, ためし, てい, なり, へ, へん, ほ, ほう, ほとり, み, むくろ, よう, らん, れい, わたり
  - Deduplicated English glosses: (dead) body; (facial) expression; (facial) features; (hen's) egg; (in the) neighbourhood; (outward) appearance; (physical) form; (resultant) state; (the) face (of something); (the) person; -looking; -wards; -ways; Mesdames; Miss; Mr; Mrs; Ms; Sirs; a ... in the making; about; adjective; air; appearance; appearance (of a person); appearing ...; area; area (in a particular direction); around; around (the time that, etc.); attendees; attire; attitude; bank; beginning; behaviour (towards others); blade (as opposed to its handle); body; budding ...; build; care of ...; case; category; causeway; chaff; chicken bones (e.g. for soup); chicken carcass; circumstances; clothing; clout; coast; collateral; condition; constitution; container (as opposed to its lid); cord (e.g. corduroy); corpse; correspondence; cosmetics; countenance; counter for Buddhist images (statues and paintings); counter for humanoid forms (e.g. dolls, statues, corpses, etc.); custom; demeanor; design; dignity; direction; dress; edge; egg; egg cell; eggs; eggshell; everyone involved; example; face; features; field; field (of study, etc.); figure; footpath between rice fields; for instance; form; form (as opposed to substance); frame; future (lawyer, doctor, etc.); gentleman; getup; habit; having the traits or characteristics of ...; health; honor (honour); hull; husk; i-adjective (in Japanese); identity; illustration; in the act of ...; indicates one side of a comparison; infancy; influence; instance; just as one is ...; lady; left-overs; length (of each side of a square); letter; like; lineup; look; looking ...; looks; main part; makes a word more polite (usu. in fixed expressions); makeup; manner; manner (of action); manner of; manner of ...; manners; means; meat (as opposed to bone, skin, etc.); members; mess; method; method of; method of ...ing; mu (Chinese measure of land area, formerly approx. 600 m.sq., currently approx. 667 m.sq.); nearby; neighborhood; neighbourhood; notoriety; obverse of an old zeni coin; one's appearance; one's body; one's part; one's person; one's place (in society, etc.); one's position; oneself; or thereabouts; origin; outfit; ovule; ovum; participants; person; person in charge of ...; personal appearance; physical system; physique; plight; pod; poor-quality coke (coal); posture; practice; precedent; pull; reality; region; remnants; representative; resembling ...; rib (cloth, mountains, sea); ridge (in field); ridge between grooves in threshold or lintel; ridge of earth between rice fields; roe; row of raised earth when planting crops; sad sight; say; se (Japanese unit of area equal to 30 tsubo, approx. 99.174 m.sq.); seashore; seaside; security; shape; shape (positional qualities of a group of stones); shell; shell (e.g. electron shell); shore; side; side (e.g. "on my mother's side"); side (esp. of a waterbody); side (of an argument, etc.); similar to; situation; sorry state; spawn; state; style; substance; such as; suffix used as a rough indicator of location, direction, time, etc.; surrounding area; surroundings; thing (thought or spoken); torso; trademark; trunk; trust; type; typeface; usage; vicinity; visage; voice; way; way of; way of ...; way to ...; with a ...-like expression; with an air of ...; wood (as opposed to bark)

- **19 written / 30 source entries / 33 readings / 91 glosses**
  - Written: ど頭 / ド頭 / 丈 / 兄 / 司 / 官 / 寮 / 氏の上 / 氏上 / 氏宗 / 氏長 / 永 / 長 / 頚 / 頭 / 頸 / 首 / 首級 / 馘
  - Entries: 1007340, 1249900, 1354600, 1429740, 1450690, 1554230, 1582310, 1592270, 1653340, 1983700, 1994890, 2145400, 2200990, 2251080, 2251110, 2252670, 2252700, 2254970, 2265230, 2525980, 2647190, 2647210, 2712010, 2791490, 2791500, 2791510, 2843246, 2856438, 2856439, 2856440
  - Readings: あたま, あに, うじのかみ, おうと, おさ, おびと, かしら, かぶし, かぶり, かん, がしら, くび, けい, こうべ, このかみ, し, しゅ, しゅきゅう, しるし, じょう, ず, たき, たけ, だけ, ちょう, つかさ, つむ, つむり, とう, どたま, なが, にい, りょう
  - Deduplicated English glosses: 3.03 meters (ten shaku); Mister; Mr; Mr.; Mrs.; Obito (hereditary title, often given to powerful regional families); all (one has); alone; as much as; bean; beginning; boss; brains; brother-in-law (spouse's older brother or older sister's husband); bureau (government department beneath a ministry under the ritsuryō system); captain; chief; counter for helmets, masks, etc.; counter for insects in a collection; counter for large animals (e.g. head of cattle); counter for songs and poems; decapitated head of an enemy; deposit; dismissal (from a job); dome; dormitory; down payment; elder; elder brother; eldest son; enough to; everything; firing; government; government official; government post; government service; hair (on one's head); head; head (of a doll); head of ...; head of a clan; head of a region; height; hostel; intellect; just; leader; length; length (esp. of clothing); long; magnificence (of a waka poem, etc.); major; measure; merely; merit; mind; neck; no more than; nob; noggin; nothing but; office; office (government department beneath a bureau under the ritsuryō system); official; older brother; older person; older sister; only; pair; person; person (usually a woman) who officiates at religious ceremonies (on the Yaeyama Islands in Okinawa); sacking; shape of one's head; simply; skilled craftsman; start; stature; strong point; superiority; tea-ceremony room; the bureaucracy; the greatest (of all the ...); the moment that ...; tip; to the extent of; top; top of ...; top structural component of a kanji; villa; you

- **18 written / 9 source entries / 9 readings / 12 glosses**
  - Written: 一位 / 団栗 / 杼 / 栃 / 栃の木 / 梭 / 椚 / 椢 / 槶 / 橡 / 橡の木 / 櫟 / 櫪 / 石櫧 / 禾予 / 赤檮 / １位 / 𥝱
  - Entries: 1161020, 1419200, 1922090, 2066140, 2127910, 2145440, 2264890, 2723840, 2853458
  - Readings: いちい, くぬぎ, くのぎ, じょ, つるばみ, とち, とちのき, どんぐり, ひ
  - Deduplicated English glosses: 10^24; Japanese horse chestnut (Aesculus turbinata); Japanese yew (Taxus cuspidata); Quercus gilva (species of oak); acorn; dark gray; dark grey; first place; first rank; sawtooth oak (Quercus acutissima); shuttle (of a loom); units position (of a number)

- **18 written / 7 source entries / 9 readings / 25 glosses**
  - Written: きっ先 / ほこ先 / 切っ先 / 切先 / 切尖 / 戈 / 戟 / 桙 / 槍 / 矛 / 矛先 / 穂先 / 鉾 / 鉾先 / 鋒 / 鋒先 / 鎗 / 鑓
  - Entries: 1400500, 1514790, 1531100, 1591300, 1603450, 1863520, 2229870
  - Readings: か, きっさき, げき, とかり, ほう, ほこ, ほこさき, ほさき, やり
  - Deduplicated English glosses: aim; arms; brunt; edge; focus (of one's attack, criticism, etc.); force (of an argument); ge (ancient Chinese dagger-axe); grip of a bow; javelin; jeering; ji (ancient Chinese halberd); lance; long-handled Chinese spear; parade float decorated with long-handled Chinese spears; pike; point; point (of a sword, etc.); point of a spear; pointed verbal attack; spear; spearhead; target; tip (of a brush, spear, etc.); tip of an ear (of wheat, etc.); weapon

- **17 written / 1 source entries / 1 readings / 2 glosses**
  - Written: 俱利伽羅紋々 / 俱利伽羅紋紋 / 俱利迦羅紋々 / 俱利迦羅紋紋 / 俱梨伽羅紋々 / 俱梨伽羅紋紋 / 俱梨迦羅紋々 / 俱梨迦羅紋紋 / 倶利伽羅もんもん / 倶利伽羅紋々 / 倶利伽羅紋紋 / 倶利迦羅紋々 / 倶利迦羅紋紋 / 倶梨伽羅紋々 / 倶梨伽羅紋紋 / 倶梨迦羅紋々 / 倶梨迦羅紋紋
  - Entries: 1872660
  - Readings: くりからもんもん
  - Deduplicated English glosses: person with a tattoo; tattoo (esp. one of a flame-wreathed dragon wrapped around and swallowing the tip of an upright sword)

- **16 written / 29 source entries / 35 readings / 151 glosses**
  - Written: 主 / 主人 / 児 / 子 / 実 / 愛 / 核 / 正身 / 直身 / 真 / 稚 / 稚児 / 若 / 誠 / 重 / 饗
  - Entries: 1150410, 1205510, 1307770, 1307780, 1315010, 1320800, 1320810, 1324940, 1324950, 1324960, 1335730, 1363170, 1363180, 1421780, 1579780, 1580555, 2108240, 2193250, 2216440, 2216570, 2216580, 2427490, 2427530, 2427540, 2427550, 2744640, 2751070, 2771710, 2860409
  - Readings: あい, あえ, あるじ, え, おも, かく, きょう, こ, こう, さね, し, しゅ, しゅう, しゅじん, しん, じ, じち, じつ, じゅう, す, そうじみ, ただみ, ちご, にし, ぬし, ね, のし, ま, まこと, まな, み, むざね, やや, ややこ, わか
  - Deduplicated English glosses: (good) result; (in broth) pieces of meat, vegetable, etc.; (one's) child; (one's) employer; (one's) lord; (one's) master; (planetary) core; -er (i.e. man who spends all his time doing ...); -er (often of young women); -fold; -ply; I; Ireland; Lord; absolutely; actually; affection; agape; attachment; baby; beloved; bird egg; boy; care; chief; chief part; child; child (esp. a boy); child (esp. of the young son of someone of high social standing); child in a traditional festival procession; clitoris; condensation nucleus; content; core; core (of an organization, team, etc.); counter for played stones; craving; dear; desire; doer (of a deed); due (east); eleventh month of the lunar calendar; entertaining someone (as one's guest); entertaining someone as one's guest; extreme; fact; faithfulness; fidelity; first consideration; founder of a school of thought (esp. Confucius); fruit; genuine; genuine article; genuineness; good faith; guardian spirit (e.g. long-resident beast, usu. with mystical powers); head; head (of a household); head (of a household, etc.); heart; heavy; honesty; honorific (or familiar) suffix used after a name; host; hostess; hour of the Rat (around midnight, 11pm to 1am, or 12 midnight to 2am); husband; important; indeed; infant; interest; just; jūbako; kernel; kid; landlady; landlord; leader; leader (of a country, group, etc.); long-time resident (or employee, etc.); love; main; main secondary or supporting role (in kyogen); main thing; master; master (of a servant); masters and philosophers (categorization of Chinese classical literature); me; multi-tiered food box; new; new share; north; nuclear weapons; nucleus; nucleus (of a cell); nucleus (of a cultured pearl); nucleus (of an atom); nut; offshoot; offspring; one's husband; owner; page; pageboy; pip; pit; player who is not a dealer; primary object; principal; principal aim; printed style writing; proprietor; proprietor (of a store); proprietress; pure; quite; real thing; reality; really; right; ring (in a cyclic compound); said person; seed; seed (of a fruit); serious; seriousness; sincerity; star performer; stone; stone (of a fruit); subject (of a rumour, etc.); substance; teenager; the Rat (first sign of the Chinese zodiac); the real person; tongue (projecting strip on a board that fits into a groove on another board); treating someone (to food or drink); true; truly; truth; very; viscount; you; young; young (animal); young (non-adult) person; young geisha; young prostitute; young woman; youngster; youth

- **16 written / 25 source entries / 29 readings / 160 glosses**
  - Written: 便 / 元 / 古 / 古え / 因 / 基 / 往古 / 所縁 / 故 / 旧 / 本 / 殊更 / 江に / 由 / 素 / 縁
  - Entries: 1013030, 1168640, 1177490, 1177500, 1230380, 1260670, 1265040, 1265060, 1267110, 1267120, 1328690, 1512360, 1512370, 1522150, 1541760, 1621290, 1719260, 1922790, 2056370, 2069220, 2141870, 2182840, 2219590, 2219600, 2827964
  - Readings: いにしえ, いん, え, えに, えにし, えん, おうこ, おうご, き, きゅう, げん, こ, ことさら, しょえん, す, そ, びん, ふち, ふる, へり, べん, ほん, もと, もとい, ゆえ, ゆかり, よし, よすが, ユアン
  - Deduplicated English glosses: (Taiwan) dollar; (free) radical; (one's) side; (raw) material; (soup) stock; (tree) trunk; ... at issue; ... in question; I hear that ...; Japan's old (lunisolar) calendar; Mongol dynasty; Yuan dynasty (of China; 1271-1368); Yüan dynasty; accommodation; affinity; aid; ancient times; antiquity; as a result of; au naturel; base; basis; because of; before; beginning; bond; book; brim; brink; bygone days; capital; cause; chance; chance (to meet someone and start a relationship); circumstances; clue; connection; connection (to a person, place, etc.); convenience; cost price; counter for falcons (in falconry); counter for films, TV shows, etc.; counter for goals, home runs, etc.; counter for installed or mounted objects (e.g. stone lanterns, gravestones, satellites); counter for long, thin objects (sticks, tubes, strings, bottles, pillars, rivers, rail lines, etc.); counter for plants or trees; counter for rounds or points (in judo, kendo, etc.); counter for telephone calls, letters, and emails; current; deliberately; designedly; destiny (esp. as a mysterious force that binds two people together); due to; earlier times; edge; edge (of a river, woods, etc.); edging; element (of a set); especially; ex-; exceedingly; excrement; excreta (esp. faeces); fabric border (of a tatami mat, etc.); facility; factor; family ties; fate; first three lines of a waka; flight (e.g. airline flight); former; former times; formerly; foundation; fringe; genuine; grounds; group; head; hem; hetu (direct cause, esp. as opposed to indirect conditions); ingredient; intentionally; it is said that ...; letter; link; mail; main; margin; means; memento; mere; mix (e.g. for a cake); narrow open-air veranda; old; old customs; old things; old times; on purpose; one's feelings; one's nature; one-time; oneself; opportunity; origin; originally; part (of a brush, chopsticks, etc.) one holds (with the hand); particularly; past; piece of information that one has heard; plain; poor; post; pratyaya (indirect conditions, as opposed to direct causes); present; previous; previous state; previously; prime; principal; principle; proper; real; reason; regular; relation; relationship (e.g. between two people); relative; reminder; rim; root; root (of a plant); sailing (e.g. ferry voyage); script; secondhand item; selvage; service; shoulder (of a road); significance; someone to rely on; something to rely on; source; stool; the basis of one's argument (in hetuvidya); the deceased; the late; the old; the past; this; trip (e.g. train trip); unadorned; unadulterated; undecorated; unknown (e.g. in an equation); used; used item; volume; way; white silk; yuan (monetary unit of China)

- **15 written / 1 source entries / 1 readings / 2 glosses**
  - Written: あることない事 / あること無いこと / あること無い事 / ある事ないこと / ある事ない事 / ある事無いこと / ある事無い事 / 在ること無いこと / 在る事無い事 / 有ることないこと / 有ること無いこと / 有ること無い事 / 有る事ない事 / 有る事無いこと / 有る事無い事
  - Entries: 2119840
  - Readings: あることないこと
  - Deduplicated English glosses: half-truth; mixture of fact and fiction

- **14 written / 22 source entries / 26 readings / 62 glosses**
  - Written: 交 / 交々 / 交交 / 大御 / 御 / 御々 / 御御 / 性 / 新 / 更 / 更け / 深 / 相 / 美
  - Entries: 1270190, 1361480, 1375250, 1375260, 1400800, 1400810, 1486350, 1593420, 2243120, 2259690, 2259700, 2259710, 2259720, 2259730, 2259740, 2260180, 2260190, 2260200, 2443200, 2826528, 2833475, 2837865
  - Readings: あい, あら, お, おおいご, おおみ, おおむ, おおん, おご, おごう, おみ, おん, ぎょ, こう, こもごも, ご, さが, さら, しょう, しん, せい, そう, なりくせ, にい, び, ふけ, み
  - Deduplicated English glosses: -cy; -ity; -ness; -ty; Gregorian calendar; Hsin dynasty; Xin dynasty (of China; 9-23 CE); adds a nuance of solemnity or formality; alternately; another's wife or daughter; appearance; aspect; association; august; august (in ref. to the emperor or the gods); beautiful; beauty; change (of season, year, etc.); character; convention; custom; disposition; divine; equally; fellowship; gender; growing late; habit; honorific affix; honorific or polite prefix; honorific prefix; honorific suffix; honorific/polite/humble prefix; imperial; in succession; latening; look; minister (of a government department); mutually; natural; nature; nature (of a person); neo-; new; newness; novelty; obvious; older lady; one's destiny; one's nature; one-fifth of the night (approx. 2 hours); phase (e.g. solid, liquid and gaseous); physiognomy (as an indication of one's fortune); quality; reciprocally; sex; sex (i.e. sexual attraction, activity, etc.); temperament; that which does not change according to external influences; together; tradition; unused

- **14 written / 11 source entries / 11 readings / 22 glosses**
  - Written: 明かし / 明かり / 明り / 灯 / 灯かり / 灯し / 灯し火 / 灯り / 灯火 / 点火 / 照射 / 燈 / 燈火 / 燭
  - Entries: 1350930, 1441440, 1582290, 1586210, 2519550, 2519560, 2519570, 2519580, 2519590, 2850327, 2850328
  - Readings: あかし, あかり, しょうしゃ, しょく, てんか, とう, とうか, とぼし, ともし, ともしび, ひ
  - Deduplicated English glosses: (a) light; baiting deer with a lighted torch; beaming; counter for electric lights; exposure (to light); firing; gleam; glow; ignition; illumination; irradiation; lamp; lamplight; light; light (esp. as a religious offering); lighting; lights; obsolete unit of luminous intensity (approx. 1.0067 candela); proof (of innocence); radiation; setting off; torch

- **14 written / 5 source entries / 5 readings / 24 glosses**
  - Written: 乃 / 之 / 之れ / 惟 / 是 / 是れ / 此 / 此れ / 此ん / 此処 / 此所 / 爰 / 維 / 茲
  - Entries: 1288810, 1469800, 1628530, 2202110, 2765700
  - Readings: ここ, これ, こん, ぜ, の
  - Deduplicated English glosses: I; here; indicates a confident conclusion; indicates emotional emphasis; indicates possessive; indicates question; justice; me; nominalizes verbs and adjectives; now; right; righteousness; substitutes for "ga" in subordinate phrases; the next ... (e.g. few days); these last ...; these next ...; these past ... (e.g. three years); this; this one; this person; this place; this point; this point (in time); used to stress the subject of a sentence

- **14 written / 4 source entries / 4 readings / 17 glosses**
  - Written: 住みか / 住み処 / 住み家 / 住処 / 住家 / 住所 / 巣 / 栖 / 栖家 / 棲 / 棲み処 / 棲み家 / 棲処 / 棲家
  - Entries: 1333970, 1334080, 1400390, 2859339
  - Readings: じゅうか, じゅうしょ, す, すみか
  - Deduplicated English glosses: (residential) house; (spider's) web; abode; address (of a home, business, etc.); breeding place; den; den (of criminals, demons, etc.); domicile; dwelling; habitat; haunt; hive; home; house; nest; residence; rookery

- **14 written / 1 source entries / 2 readings / 5 glosses**
  - Written: いやという程 / いやと言うほど / いやと言う程 / イヤという程 / イヤと言うほど / イヤと言う程 / 厭というほど / 厭と言うほど / 否というほど / 否と言うほど / 嫌というほど / 嫌という程 / 嫌と言うほど / 嫌と言う程
  - Entries: 1482900
  - Readings: いやというほど, イヤというほど
  - Deduplicated English glosses: (more than) enough; bitterly; extremely; keenly; too much

- **14 written / 1 source entries / 1 readings / 6 glosses**
  - Written: 一か所 / 一カ所 / 一ケ所 / 一ヵ所 / 一ヶ所 / 一個所 / 一箇所 / １か所 / １カ所 / １ケ所 / １ヵ所 / １ヶ所 / １個所 / １箇所
  - Entries: 1587420
  - Readings: いっかしょ
  - Deduplicated English glosses: (all in) the same place; one location; one part; one passage; one place; one spot

- **14 written / 1 source entries / 1 readings / 2 glosses**
  - Written: けんかするほど仲がいい / けんかするほど仲がよい / けんかするほど仲が良い / ケンカするほど仲がいい / ケンカするほど仲がよい / ケンカするほど仲が良い / ケンカする程仲がいい / ケンカする程仲が良い / 喧嘩するほどなかがいい / 喧嘩するほど仲がいい / 喧嘩するほど仲がよい / 喧嘩するほど仲が良い / 喧嘩する程仲がいい / 喧嘩する程仲が良い
  - Entries: 2869699
  - Readings: けんかするほどなかがいい
  - Deduplicated English glosses: the more (people) fight, the closer they are; those who quarrel most are often the best of friends

- **13 written / 11 source entries / 12 readings / 44 glosses**
  - Written: 尽 / 尽く / 悉く / 捗 / 果 / 果たて / 果て / 極 / 極く / 涯 / 涯て / 計 / 量
  - Entries: 1004570, 1192860, 1240180, 1252050, 1554640, 1956100, 2187130, 2270990, 2436470, 2436480, 2817380
  - Readings: か, きょく, けい, ことごとく, ごく, じん, ずく, づく, はか, はたて, はて, りょう
  - Deduplicated English glosses: (in) total; 10^48; altogether; amount; based on (mutual consent, etc.); capacity; climax; consequence; counter for pieces of fruit; culmination; end; enlightenment (as the fruits of one's Buddhist practice); entirely; extreme; extremity; fruit; generosity; height; last day (of the month); limit; magnanimity; measuring device; meter; nadir; outcome; phala (attained state, result); plan; pole; portion (of food); pramana (means by which one gains accurate and valid knowledge; in Indian philosophy); progress; quantity; quindecillion; quite; relying entirely on ...; result; tolerance; total (of); upshot; using solely ...; very; volume; with the sole purpose of ...; zenith

- **13 written / 1 source entries / 1 readings / 2 glosses**
  - Written: すり硝子 / 摺りガラス / 摺り硝子 / 摺ガラス / 摺硝子 / 擦りガラス / 擦り硝子 / 擦ガラス / 擦硝子 / 磨りガラス / 磨り硝子 / 磨ガラス / 磨硝子
  - Entries: 1595900
  - Readings: すりガラス
  - Deduplicated English glosses: frosted glass; ground glass

- **13 written / 1 source entries / 1 readings / 9 glosses**
  - Written: 立ち居ふるまい / 立ち居振るまい / 立ち居振る舞 / 立ち居振る舞い / 立ち居振舞 / 立ち居振舞い / 立居ふるまい / 立居振る舞い / 立居振舞 / 立居振舞い / 起ち居振る舞い / 起ち居振舞い / 起居振舞
  - Entries: 1838050
  - Readings: たちいふるまい
  - Deduplicated English glosses: bearing; behavior; behaviour; carriage; demeanor; demeanour; deportment; manners; movements

- **13 written / 1 source entries / 6 readings / 3 glosses**
  - Written: らちが明かない / らちが明かぬ / らちが明かん / ラチが明かない / ラチが明かぬ / ラチが明かん / ラチが開かない / 埒があかない / 埒があかぬ / 埒があかん / 埒が明かない / 埒が明かぬ / 埒が明かん
  - Entries: 1915350
  - Readings: らちがあかない, らちがあかぬ, らちがあかん, ラチがあかない, ラチがあかぬ, ラチがあかん
  - Deduplicated English glosses: getting nowhere; making no progress; remaining unsettled

- **13 written / 1 source entries / 1 readings / 1 glosses**
  - Written: わかり合う / 分かりあう / 分かり合う / 分りあう / 分り合う / 判りあう / 判り合う / 理解りあう / 理解り合う / 解かりあう / 解かり合う / 解りあう / 解り合う
  - Entries: 2038420
  - Readings: わかりあう
  - Deduplicated English glosses: to understand each other

- **13 written / 1 source entries / 1 readings / 2 glosses**
  - Written: げすの勘ぐり / げすの勘繰り / ゲスの勘ぐり / ゲスの勘繰り / 下司のかんぐり / 下司の勘ぐり / 下司の勘繰り / 下種のかんぐり / 下種の勘ぐり / 下種の勘繰り / 下衆のかんぐり / 下衆の勘ぐり / 下衆の勘繰り
  - Entries: 2838728
  - Readings: げすのかんぐり
  - Deduplicated English glosses: petty-minded suspicion; putting a deliberately bad interpretation (on a person's actions)

- **12 written / 14 source entries / 16 readings / 68 glosses**
  - Written: 后 / 妃 / 孔 / 尻 / 後 / 後え / 後ろ / 後方 / 穴 / 臀 / 針孔 / 針眼
  - Entries: 1254480, 1254490, 1269320, 1269330, 1269410, 1270100, 1277320, 1358760, 1956860, 2147630, 2159590, 2854165, 2861812, 2862495
  - Readings: あと, あな, うしろ, きさい, きさき, けつ, こうほう, ご, しり, しりえ, のち, はりめど, ひ, みず, みぞ, めど
  - Deduplicated English glosses: (financial) hole; (partitioned) box (type of floor seating in a traditional Japanese theatre); acupuncture point; after; after one's death; afterwards; also; arse; ass; back; behind; bottom; burrow; buttocks; cavity; consequence; consort; dark horse; defect; deficit; den; descendant; empress (consort); end; eye of a needle; fault; flaw; future; gap (left by a missing employee, team member, etc.); good place not many people know about; heir; hideout; hiding place; hips; hole; hole (e.g. in the ground); hole (in a theory, plan, etc.); hollow; holt; in addition; lair; last place; later; left; long shot; loophole; loss; more (e.g. five more minutes); notch; opening; orifice; past; perforation; pit; previous; princess; queen (consort); rear; remainder; rump; shortage; successor; the rest; undersurface; upset victory (with a large payoff); vacancy; weak point; well-kept secret

- **12 written / 10 source entries / 12 readings / 30 glosses**
  - Written: たて糸 / 四 / 恣 / 擅 / 歴 / 竪 / 経 / 経糸 / 縦 / 縦し / 縦糸 / 肆
  - Entries: 1335640, 1579470, 1661350, 1908340, 1956170, 2211960, 2607700, 2747960, 2783730, 2826444
  - Readings: きょう, けい, し, たて, たていと, ふ, ほしいまま, よ, よし, よん, れき, スー
  - Deduplicated English glosses: (weaving) warp; 4; Buddhist scriptures; arbitrary; career; even if; experience; four; front-to-back; height; hierarchy; history; length; longitude; north-to-south; record; scripture; self-indulgent; selfish; sutra; the vertical; to elapse; to experience; to go by; to go through; to pass; to pass through; trans-; vertical (relationship); warp (weaving)

- **12 written / 10 source entries / 10 readings / 55 glosses**
  - Written: 倭 / 凪 / 和 / 和ぎ / 和ら / 大和 / 暇 / 柔 / 柔ら / 軟ら / 遑 / 閑
  - Entries: 1459650, 1561920, 1577280, 1585330, 2039460, 2437530, 2709280, 2763050, 2812930, 2858986
  - Readings: いとま, かん, じゅう, なぎ, のど, ひま, やまと, やわ, やわら, わ
  - Deduplicated English glosses: (ancient) Japan; (spare) time; Japan; Japanese-style; Yamato (battleship); Yamato (former province located in present-day Nara Prefecture); Yamato kingdom; calm; calm (at sea); dismissal; divorce; dull; farewell; firing; fragile; free; free time; gentleness; harmony; holiday; idle; insubstantial; judo; jujutsu; leave; leaving; leisure; lull; not busy; peace; peaceful; poorly built; quiet; resignation; severance; slack; slow (business); soft; softness; spare time; state of Yamato; sum; taking one's leave; time (it takes to do something); time off; time required; to be available; to be doing nothing; to be free; to be not busy; tranquil; unoccupied; vacation; weak; weakness

- **12 written / 8 source entries / 8 readings / 2 glosses**
  - Written: 不如帰 / 子規 / 時鳥 / 杜宇 / 杜鵑 / 沓手鳥 / 沓直鳥 / 田鵑 / 蜀魂 / 蜀魄 / 郭公 / 霍公鳥
  - Entries: 1206270, 1444570, 2861877, 2861878, 2861879, 2861880, 2861881, 2861882
  - Readings: かっこう, くつてどり, しき, しょっこん, とう, とけん, ふじょき, ほととぎす
  - Deduplicated English glosses: common cuckoo (Cuculus canorus); lesser cuckoo (Cuculus poliocephalus)

- **12 written / 2 source entries / 2 readings / 2 glosses**
  - Written: 活き作り / 活き造り / 活け作り / 活け造り / 活作り / 活造り / 生き作り / 生き造り / 生け作り / 生け造り / 生作り / 生造り
  - Entries: 1664800, 1829920
  - Readings: いきづくり, いけづくり
  - Deduplicated English glosses: fresh sashimi; sashimi prepared from live fish, arranged in its original shape

- **12 written / 1 source entries / 1 readings / 1 glosses**
  - Written: 一か月 / 一カ月 / 一ケ月 / 一ヵ月 / 一ヶ月 / 一箇月 / １か月 / １カ月 / １ケ月 / １ヵ月 / １ヶ月 / １箇月
  - Entries: 1587410
  - Readings: いっかげつ
  - Deduplicated English glosses: one month

- **12 written / 1 source entries / 1 readings / 7 glosses**
  - Written: かけ引き / 懸け引き / 掛け引き / 掛引 / 駆けひき / 駆け引 / 駆け引き / 駆引 / 駆引き / 駈け引き / 駈引 / 駈引き
  - Entries: 1590130
  - Readings: かけひき
  - Deduplicated English glosses: (tactical) advance or retreat of troops; bargaining; diplomacy; haggling; maneuvering; strategy; tactics

- **12 written / 1 source entries / 1 readings / 3 glosses**
  - Written: 乾しぶどう / 乾しブドウ / 乾し葡萄 / 乾ぶどう / 乾ブドウ / 乾葡萄 / 干しぶどう / 干しブドウ / 干し葡萄 / 干ぶどう / 干ブドウ / 干葡萄
  - Entries: 1603480
  - Readings: ほしぶどう
  - Deduplicated English glosses: currants; dried grapes; raisins

## 11. Transitive merge risks

| Source entries per group threshold | Groups |
| --- | --- |
| >1 | 2 839 |
| >2 | 566 |
| >5 | 63 |
| >10 | 15 |
| Maximum entries | 61 |

Всего 2 839 / 171 585 groups (1.65%) multi-entry. Extreme graph chains связывают визуально и семантически разные объекты. Большая компонента — не только duplicate entries одного слова. Ниже субъективная оценка по написаниям/readings/glosses; она не меняет диагностический algorithm.

- Exact-link path 男 ↔ 女: 男 —[2742870]— 夫 → 夫 —[2852371]— 妻 → 妻 —[2742880]— 女
- Exact-link path 男 ↔ 俺: 男 —[2742870]— 夫 → 夫 —[2754880]— 其 → 其 —[2174430]— 汝 → 汝 —[1631650]— 己 → 己 —[1576870]— 俺
- Exact-link path 卵 ↔ 身体: 卵 —[2647520]— 殻 → 殻 —[1205740]— 骸 → 骸 —[1956130]— 躯 → 躯 —[1409140]— 身体
- Exact-link path 前 ↔ 先: 前 —[1387210]— 先

Source check цепочки 男→女: entry 2742870 written 雄/男/夫/牡, reading お, включает male/man/husband; entry 2852371 written 妻/夫, reading つま, glosses my dear/dear/honey; entry 2742880 written 雌/女/妻/牝, reading め, включает female/woman/wife. Источник действительно содержит эти edges; это не ошибка union-find.

- **48 written / 61 source entries / 77 readings / 178 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: おとこの娘 / おのれ等 / 俺 / 俺ら / 俺等 / 側 / 側ら / 側女 / 側妻 / 傍 / 傍ら / 儞 / 其 / 其れ / 其方 / 具 / 助 / 夫 / 女 / 妻 / 己 / 己ら / 己れ / 己れら / 己れ等 / 己等 / 掖 / 旁 / 旁々 / 旁旁 / 汝 / 汝等 / 然 / 爾 / 牝 / 牡 / 牸 / 男 / 男のコ / 男の娘 / 男の子 / 端 / 端た / 脇 / 腋 / 良人 / 雄 / 雌
  - Entries: 1006780, 1006970, 1244950, 1266890, 1294330, 1312880, 1344360, 1344370, 1344930, 1344940, 1394690, 1403830, 1419990, 1420010, 1496480, 1562530, 1567860, 1576870, 1576880, 1578530, 1581310, 1581610, 1589190, 1590370, 1590440, 1631650, 1634450, 2015140, 2080360, 2084820, 2173530, 2174410, 2174420, 2174430, 2174450, 2174460, 2174490, 2174500, 2174510, 2590770, 2735810, 2742850, 2742870, 2742880, 2742890, 2746040, 2746070, 2754880, 2764560, 2826522, 2836662, 2837821, 2840654, 2843131, 2843485, 2852371, 2860163, 2862416, 2867582, 2869001, 2870787
  - Readings: い, いまし, うぬ, うぬら, うら, お, おいら, おす, おっこ, おっと, おとこ, おとこのこ, おどれ, おぬ, おの, おのこ, おのれ, おのれら, おみな, おら, おらあ, おり, おれ, おれら, おん, おんどれ, おんな, かたがた, かたわら, かわ, がわ, き, ぐ, さ, さい, し, しか, しゃ, じょ, すけ, ぜん, そ, そく, そちら, そっち, そなた, そば, そばめ, それ, たん, だん, つくり, つちのと, つま, な, なむたち, なむだち, なむち, なれ, なん, なんじ, はし, はした, はじ, はた, はな, ふ, まし, みまし, め, めす, めん, ゆう, りょうじん, りょうにん, わいら, わき
  - Deduplicated English glosses: (place) close by; (place) near by; (place) right next to; (someone's) woman; (watch) case; (working) man; -like; 6th in rank; Chinese "Girl" constellation (one of the 28 mansions); I; accompaniment; armpit; as soon as; as such; aside; assistance; assistant; at the same time; away; babe; baby boy; baron; beginning; beside; besides; best (of); blockhead!; bloke; boy; boyfriend; brave; broad; by; by oneself (itself, etc.); bystanders; chap; chick; close female servant; close to; concubine; counter for sets of armor, utensils, furniture, etc.; daughter; dear; deuteragonist; edge; end; end (e.g. of street); excellence; extremity; fellow; female; female (animal, plant); female lover; femboy; feminine noun; first; first principle of the Eight Principles of Yong; flank; for haori: at least 7.27 m in length; for kimonos: at least 10 m in length; for other clothes: at least 6.06 m in length; fraction; gable; gable wall; garnish (esp. one served with sashimi); girl; girlfriend; good person; great person; greater; greatness; guy; he; help; helper; heroic; honey; husband; in addition to; in that way; incidentally; ingredients (added to soup, rice, etc.); interjection expressing anger or chagrin; it; kept woman; laborer; larger (of the two); leading figure; least; like that; low class female servant; male; male (animal, plant); male lover; man; manliness; manly; manly honor; manly honour; manly reputation; margin; masculine noun; me; means; mistress; my dear; myself; near; nearby; neighbourhood (neighborhood); odd bit; odd money; odd sum; odds and ends; off-topic; off-track; one's wife; oneself; oneself (itself, etc.); onlookers; origin; others; out of the way; outsiders; part; point; proximity; rescue; right after; right-hand radical of a character; scrap; second verse (in a linked series of poems); she; side; side (of something, or taking someone's side); side (remark); sixth sign of the Chinese calendar; small change; smaller (of the two); so; son; start; supporting role; that; that (one); that direction; that person; that point (in time); that time; that way; the side (of something); themself; then; there; third person; tiny dash or speck; tip; to the side; tool; trimmings; uh-huh; under one's arm; us; variable measure of fabric (28.8 cm in width); vicinity; we; weaker; while (doing); wife; woman; ye; yeah; you; you (plural); young male with a feminine appearance (esp. one who crossdresses and has a feminine gender expression); young man; your company; your family

- **34 written / 40 source entries / 44 readings / 213 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 体 / 例 / 例し / 卵 / 容 / 形 / 態 / 方 / 様 / 殻 / 滸 / 状 / 玉子 / 畔 / 畝 / 畦 / 稃 / 貌 / 身 / 身体 / 身體 / 躯 / 躰 / 軀 / 辺 / 辺り / 顏 / 顔 / 顔かたち / 顔容 / 顔形 / 顔貌 / 骸 / 體
  - Entries: 1205740, 1217730, 1217800, 1250220, 1250980, 1356690, 1365520, 1374420, 1374430, 1409140, 1409150, 1409160, 1410750, 1512070, 1512080, 1516925, 1516930, 1545790, 1549140, 1585230, 1605840, 1956130, 2068070, 2097910, 2101970, 2138450, 2210770, 2221310, 2253290, 2254010, 2254960, 2396240, 2427890, 2647520, 2647530, 2713240, 2830705, 2842190, 2842191, 2868628
  - Readings: あぜ, あたり, うね, かい, かいご, かお, かおかたち, かく, かた, かたち, から, からだ, かんばせ, がお, がた, がら, がんぼう, がんよう, く, くろ, けい, さま, ざま, しんたい, しんだい, しんてい, じょう, せ, たい, たまご, ためし, てい, なり, へ, へん, ほ, ほう, ほとり, み, むくろ, よう, らん, れい, わたり
  - Deduplicated English glosses: (dead) body; (facial) expression; (facial) features; (hen's) egg; (in the) neighbourhood; (outward) appearance; (physical) form; (resultant) state; (the) face (of something); (the) person; -looking; -wards; -ways; Mesdames; Miss; Mr; Mrs; Ms; Sirs; a ... in the making; about; adjective; air; appearance; appearance (of a person); appearing ...; area; area (in a particular direction); around; around (the time that, etc.); attendees; attire; attitude; bank; beginning; behaviour (towards others); blade (as opposed to its handle); body; budding ...; build; care of ...; case; category; causeway; chaff; chicken bones (e.g. for soup); chicken carcass; circumstances; clothing; clout; coast; collateral; condition; constitution; container (as opposed to its lid); cord (e.g. corduroy); corpse; correspondence; cosmetics; countenance; counter for Buddhist images (statues and paintings); counter for humanoid forms (e.g. dolls, statues, corpses, etc.); custom; demeanor; design; dignity; direction; dress; edge; egg; egg cell; eggs; eggshell; everyone involved; example; face; features; field; field (of study, etc.); figure; footpath between rice fields; for instance; form; form (as opposed to substance); frame; future (lawyer, doctor, etc.); gentleman; getup; habit; having the traits or characteristics of ...; health; honor (honour); hull; husk; i-adjective (in Japanese); identity; illustration; in the act of ...; indicates one side of a comparison; infancy; influence; instance; just as one is ...; lady; left-overs; length (of each side of a square); letter; like; lineup; look; looking ...; looks; main part; makes a word more polite (usu. in fixed expressions); makeup; manner; manner (of action); manner of; manner of ...; manners; means; meat (as opposed to bone, skin, etc.); members; mess; method; method of; method of ...ing; mu (Chinese measure of land area, formerly approx. 600 m.sq., currently approx. 667 m.sq.); nearby; neighborhood; neighbourhood; notoriety; obverse of an old zeni coin; one's appearance; one's body; one's part; one's person; one's place (in society, etc.); one's position; oneself; or thereabouts; origin; outfit; ovule; ovum; participants; person; person in charge of ...; personal appearance; physical system; physique; plight; pod; poor-quality coke (coal); posture; practice; precedent; pull; reality; region; remnants; representative; resembling ...; rib (cloth, mountains, sea); ridge (in field); ridge between grooves in threshold or lintel; ridge of earth between rice fields; roe; row of raised earth when planting crops; sad sight; say; se (Japanese unit of area equal to 30 tsubo, approx. 99.174 m.sq.); seashore; seaside; security; shape; shape (positional qualities of a group of stones); shell; shell (e.g. electron shell); shore; side; side (e.g. "on my mother's side"); side (esp. of a waterbody); side (of an argument, etc.); similar to; situation; sorry state; spawn; state; style; substance; such as; suffix used as a rough indicator of location, direction, time, etc.; surrounding area; surroundings; thing (thought or spoken); torso; trademark; trunk; trust; type; typeface; usage; vicinity; visage; voice; way; way of; way of ...; way to ...; with a ...-like expression; with an air of ...; wood (as opposed to bark)

- **19 written / 30 source entries / 33 readings / 91 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: ど頭 / ド頭 / 丈 / 兄 / 司 / 官 / 寮 / 氏の上 / 氏上 / 氏宗 / 氏長 / 永 / 長 / 頚 / 頭 / 頸 / 首 / 首級 / 馘
  - Entries: 1007340, 1249900, 1354600, 1429740, 1450690, 1554230, 1582310, 1592270, 1653340, 1983700, 1994890, 2145400, 2200990, 2251080, 2251110, 2252670, 2252700, 2254970, 2265230, 2525980, 2647190, 2647210, 2712010, 2791490, 2791500, 2791510, 2843246, 2856438, 2856439, 2856440
  - Readings: あたま, あに, うじのかみ, おうと, おさ, おびと, かしら, かぶし, かぶり, かん, がしら, くび, けい, こうべ, このかみ, し, しゅ, しゅきゅう, しるし, じょう, ず, たき, たけ, だけ, ちょう, つかさ, つむ, つむり, とう, どたま, なが, にい, りょう
  - Deduplicated English glosses: 3.03 meters (ten shaku); Mister; Mr; Mr.; Mrs.; Obito (hereditary title, often given to powerful regional families); all (one has); alone; as much as; bean; beginning; boss; brains; brother-in-law (spouse's older brother or older sister's husband); bureau (government department beneath a ministry under the ritsuryō system); captain; chief; counter for helmets, masks, etc.; counter for insects in a collection; counter for large animals (e.g. head of cattle); counter for songs and poems; decapitated head of an enemy; deposit; dismissal (from a job); dome; dormitory; down payment; elder; elder brother; eldest son; enough to; everything; firing; government; government official; government post; government service; hair (on one's head); head; head (of a doll); head of ...; head of a clan; head of a region; height; hostel; intellect; just; leader; length; length (esp. of clothing); long; magnificence (of a waka poem, etc.); major; measure; merely; merit; mind; neck; no more than; nob; noggin; nothing but; office; office (government department beneath a bureau under the ritsuryō system); official; older brother; older person; older sister; only; pair; person; person (usually a woman) who officiates at religious ceremonies (on the Yaeyama Islands in Okinawa); sacking; shape of one's head; simply; skilled craftsman; start; stature; strong point; superiority; tea-ceremony room; the bureaucracy; the greatest (of all the ...); the moment that ...; tip; to the extent of; top; top of ...; top structural component of a kanji; villa; you

- **16 written / 29 source entries / 35 readings / 151 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 主 / 主人 / 児 / 子 / 実 / 愛 / 核 / 正身 / 直身 / 真 / 稚 / 稚児 / 若 / 誠 / 重 / 饗
  - Entries: 1150410, 1205510, 1307770, 1307780, 1315010, 1320800, 1320810, 1324940, 1324950, 1324960, 1335730, 1363170, 1363180, 1421780, 1579780, 1580555, 2108240, 2193250, 2216440, 2216570, 2216580, 2427490, 2427530, 2427540, 2427550, 2744640, 2751070, 2771710, 2860409
  - Readings: あい, あえ, あるじ, え, おも, かく, きょう, こ, こう, さね, し, しゅ, しゅう, しゅじん, しん, じ, じち, じつ, じゅう, す, そうじみ, ただみ, ちご, にし, ぬし, ね, のし, ま, まこと, まな, み, むざね, やや, ややこ, わか
  - Deduplicated English glosses: (good) result; (in broth) pieces of meat, vegetable, etc.; (one's) child; (one's) employer; (one's) lord; (one's) master; (planetary) core; -er (i.e. man who spends all his time doing ...); -er (often of young women); -fold; -ply; I; Ireland; Lord; absolutely; actually; affection; agape; attachment; baby; beloved; bird egg; boy; care; chief; chief part; child; child (esp. a boy); child (esp. of the young son of someone of high social standing); child in a traditional festival procession; clitoris; condensation nucleus; content; core; core (of an organization, team, etc.); counter for played stones; craving; dear; desire; doer (of a deed); due (east); eleventh month of the lunar calendar; entertaining someone (as one's guest); entertaining someone as one's guest; extreme; fact; faithfulness; fidelity; first consideration; founder of a school of thought (esp. Confucius); fruit; genuine; genuine article; genuineness; good faith; guardian spirit (e.g. long-resident beast, usu. with mystical powers); head; head (of a household); head (of a household, etc.); heart; heavy; honesty; honorific (or familiar) suffix used after a name; host; hostess; hour of the Rat (around midnight, 11pm to 1am, or 12 midnight to 2am); husband; important; indeed; infant; interest; just; jūbako; kernel; kid; landlady; landlord; leader; leader (of a country, group, etc.); long-time resident (or employee, etc.); love; main; main secondary or supporting role (in kyogen); main thing; master; master (of a servant); masters and philosophers (categorization of Chinese classical literature); me; multi-tiered food box; new; new share; north; nuclear weapons; nucleus; nucleus (of a cell); nucleus (of a cultured pearl); nucleus (of an atom); nut; offshoot; offspring; one's husband; owner; page; pageboy; pip; pit; player who is not a dealer; primary object; principal; principal aim; printed style writing; proprietor; proprietor (of a store); proprietress; pure; quite; real thing; reality; really; right; ring (in a cyclic compound); said person; seed; seed (of a fruit); serious; seriousness; sincerity; star performer; stone; stone (of a fruit); subject (of a rumour, etc.); substance; teenager; the Rat (first sign of the Chinese zodiac); the real person; tongue (projecting strip on a board that fits into a groove on another board); treating someone (to food or drink); true; truly; truth; very; viscount; you; young; young (animal); young (non-adult) person; young geisha; young prostitute; young woman; youngster; youth

- **16 written / 25 source entries / 29 readings / 160 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 便 / 元 / 古 / 古え / 因 / 基 / 往古 / 所縁 / 故 / 旧 / 本 / 殊更 / 江に / 由 / 素 / 縁
  - Entries: 1013030, 1168640, 1177490, 1177500, 1230380, 1260670, 1265040, 1265060, 1267110, 1267120, 1328690, 1512360, 1512370, 1522150, 1541760, 1621290, 1719260, 1922790, 2056370, 2069220, 2141870, 2182840, 2219590, 2219600, 2827964
  - Readings: いにしえ, いん, え, えに, えにし, えん, おうこ, おうご, き, きゅう, げん, こ, ことさら, しょえん, す, そ, びん, ふち, ふる, へり, べん, ほん, もと, もとい, ゆえ, ゆかり, よし, よすが, ユアン
  - Deduplicated English glosses: (Taiwan) dollar; (free) radical; (one's) side; (raw) material; (soup) stock; (tree) trunk; ... at issue; ... in question; I hear that ...; Japan's old (lunisolar) calendar; Mongol dynasty; Yuan dynasty (of China; 1271-1368); Yüan dynasty; accommodation; affinity; aid; ancient times; antiquity; as a result of; au naturel; base; basis; because of; before; beginning; bond; book; brim; brink; bygone days; capital; cause; chance; chance (to meet someone and start a relationship); circumstances; clue; connection; connection (to a person, place, etc.); convenience; cost price; counter for falcons (in falconry); counter for films, TV shows, etc.; counter for goals, home runs, etc.; counter for installed or mounted objects (e.g. stone lanterns, gravestones, satellites); counter for long, thin objects (sticks, tubes, strings, bottles, pillars, rivers, rail lines, etc.); counter for plants or trees; counter for rounds or points (in judo, kendo, etc.); counter for telephone calls, letters, and emails; current; deliberately; designedly; destiny (esp. as a mysterious force that binds two people together); due to; earlier times; edge; edge (of a river, woods, etc.); edging; element (of a set); especially; ex-; exceedingly; excrement; excreta (esp. faeces); fabric border (of a tatami mat, etc.); facility; factor; family ties; fate; first three lines of a waka; flight (e.g. airline flight); former; former times; formerly; foundation; fringe; genuine; grounds; group; head; hem; hetu (direct cause, esp. as opposed to indirect conditions); ingredient; intentionally; it is said that ...; letter; link; mail; main; margin; means; memento; mere; mix (e.g. for a cake); narrow open-air veranda; old; old customs; old things; old times; on purpose; one's feelings; one's nature; one-time; oneself; opportunity; origin; originally; part (of a brush, chopsticks, etc.) one holds (with the hand); particularly; past; piece of information that one has heard; plain; poor; post; pratyaya (indirect conditions, as opposed to direct causes); present; previous; previous state; previously; prime; principal; principle; proper; real; reason; regular; relation; relationship (e.g. between two people); relative; reminder; rim; root; root (of a plant); sailing (e.g. ferry voyage); script; secondhand item; selvage; service; shoulder (of a road); significance; someone to rely on; something to rely on; source; stool; the basis of one's argument (in hetuvidya); the deceased; the late; the old; the past; this; trip (e.g. train trip); unadorned; unadulterated; undecorated; unknown (e.g. in an equation); used; used item; volume; way; white silk; yuan (monetary unit of China)

- **14 written / 22 source entries / 26 readings / 62 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 交 / 交々 / 交交 / 大御 / 御 / 御々 / 御御 / 性 / 新 / 更 / 更け / 深 / 相 / 美
  - Entries: 1270190, 1361480, 1375250, 1375260, 1400800, 1400810, 1486350, 1593420, 2243120, 2259690, 2259700, 2259710, 2259720, 2259730, 2259740, 2260180, 2260190, 2260200, 2443200, 2826528, 2833475, 2837865
  - Readings: あい, あら, お, おおいご, おおみ, おおむ, おおん, おご, おごう, おみ, おん, ぎょ, こう, こもごも, ご, さが, さら, しょう, しん, せい, そう, なりくせ, にい, び, ふけ, み
  - Deduplicated English glosses: -cy; -ity; -ness; -ty; Gregorian calendar; Hsin dynasty; Xin dynasty (of China; 9-23 CE); adds a nuance of solemnity or formality; alternately; another's wife or daughter; appearance; aspect; association; august; august (in ref. to the emperor or the gods); beautiful; beauty; change (of season, year, etc.); character; convention; custom; disposition; divine; equally; fellowship; gender; growing late; habit; honorific affix; honorific or polite prefix; honorific prefix; honorific suffix; honorific/polite/humble prefix; imperial; in succession; latening; look; minister (of a government department); mutually; natural; nature; nature (of a person); neo-; new; newness; novelty; obvious; older lady; one's destiny; one's nature; one-fifth of the night (approx. 2 hours); phase (e.g. solid, liquid and gaseous); physiognomy (as an indication of one's fortune); quality; reciprocally; sex; sex (i.e. sexual attraction, activity, etc.); temperament; that which does not change according to external influences; together; tradition; unused

- **9 written / 17 source entries / 24 readings / 15 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 儂 / 妾 / 手懸 / 手懸け / 手掛 / 手掛け / 目掛 / 目掛け / 私
  - Entries: 1311110, 1311125, 1347580, 1699590, 2015370, 2079310, 2217330, 2217340, 2256130, 2728300, 2842390, 2843112, 2845454, 2858221, 2858384, 2858397, 2864027
  - Readings: あたい, あたくし, あたし, あっし, あて, おんなめ, し, しょう, てかけ, めかけ, わい, わし, わたい, わたくし, わたし, わちき, わっし, わっち, わて, わらわ, アタシ, ワイ, ワタクシ, ワタシ
  - Deduplicated English glosses: I; concubine; confidentiality; handle; kept woman; me; mistress; partiality; personal (affairs, etc.); personal matter; private; private affairs; secrecy; selfishness; you

- **7 written / 16 source entries / 16 readings / 121 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: ん家 / 中 / 内 / 家 / 屋 / 裏 / 裡
  - Entries: 1191730, 1191740, 1191750, 1423310, 1457730, 1550190, 1620400, 2082160, 2083570, 2098290, 2220280, 2220300, 2754230, 2841132, 2842899, 2858378
  - Readings: いえ, うち, うら, おく, か, け, じゅう, ち, ちゅう, ぢゅう, ない, なか, や, り, んち, チュン
  - Deduplicated English glosses: 's home; 's house; (all) through (e.g. the night); (out) of; -er; -ist; China; I; all (of a group); all ... long; all over; amid; amidst; among; amongst; average; average (grade, level, etc.); back; behind; behind (someone's) back; behind the scenes; between; bottom; bottom (of an inning); building; by the end of; center; centre; contrary; currently (doing); different side; during; dwelling; emperor; family; family name; gap; hidden side (e.g. of one's personality); home; house; household; imperial palace grounds; in; in (e.g. secret); in (secret, chaos, poverty, etc.); in (the course of); in the course of; in the course of (e.g. today); in the middle of; in the process of; in the shadows; inner thoughts; inside; inside ...; interior; interval; inverse (of a hypothesis, etc.); junior high school; last half (of an inning); lineage; lining; me; mean; medium (size); medium grade; medium quality; middle; middle school; middle way; midst; moderation; more (to something than meets the eye); my husband; my spouse; my wife; offstage; one's family; one's feelings; one's home; one's house; one's household; opposite (of a prediction, common sense, etc.); opposite side; other side; our company; our organization; out of (e.g. ten people); proof; rear; red dragon tile; red-light district; residence; restaurant; reverse side; roof; second (of three, e.g. sons, volumes); second volume (of three); shop; side hidden from view; signed on behalf of (husband's name) by his wife; someone who sells (something) or works as (something); someone with a (certain) personality trait; store; the whole (family, school, etc.); throughout; throughout (e.g. the world); throughout (e.g. the year); under (e.g. construction, investigation); undersurface; unknown circumstances; we; while; while (e.g. one is young); winning hand with a pung (or kong) of red dragon tiles; with (e.g. success); within; within (a period); within (e.g. a day); within (e.g. the week); within ...; within oneself

- **12 written / 14 source entries / 16 readings / 68 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 后 / 妃 / 孔 / 尻 / 後 / 後え / 後ろ / 後方 / 穴 / 臀 / 針孔 / 針眼
  - Entries: 1254480, 1254490, 1269320, 1269330, 1269410, 1270100, 1277320, 1358760, 1956860, 2147630, 2159590, 2854165, 2861812, 2862495
  - Readings: あと, あな, うしろ, きさい, きさき, けつ, こうほう, ご, しり, しりえ, のち, はりめど, ひ, みず, みぞ, めど
  - Deduplicated English glosses: (financial) hole; (partitioned) box (type of floor seating in a traditional Japanese theatre); acupuncture point; after; after one's death; afterwards; also; arse; ass; back; behind; bottom; burrow; buttocks; cavity; consequence; consort; dark horse; defect; deficit; den; descendant; empress (consort); end; eye of a needle; fault; flaw; future; gap (left by a missing employee, team member, etc.); good place not many people know about; heir; hideout; hiding place; hips; hole; hole (e.g. in the ground); hole (in a theory, plan, etc.); hollow; holt; in addition; lair; last place; later; left; long shot; loophole; loss; more (e.g. five more minutes); notch; opening; orifice; past; perforation; pit; previous; princess; queen (consort); rear; remainder; rump; shortage; successor; the rest; undersurface; upset victory (with a large payoff); vacancy; weak point; well-kept secret

- **7 written / 14 source entries / 15 readings / 84 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 初 / 初め / 初心 / 始め / 生 / 生り / 産
  - Entries: 1342530, 1342540, 1342850, 1378440, 1378450, 1953020, 2036160, 2088240, 2106250, 2416400, 2416410, 2416420, 2646830, 2863781
  - Readings: いく, うい, うぶ, き, さん, しょう, しょしん, せい, ぞめ, なま, なまり, なり, はじめ, はつ, ふ
  - Deduplicated English glosses: (giving) birth; -bred; -grown; I; area of thick growth (of trees, grass, etc.); as it is; assets; bareback; bearing (fruit); beginning; birth-; blank (e.g. disk); boiled and half-dried bonito; cash; childbirth; crude; delivery; draft beer; draught beer; first; first (in line, etc.); first doing of ... (ever, in one's life, in the new year, etc.); fortune; fresh; green; greenness; half-; half-baked; half-hearted; impudence; incomplete; inexperience; inexperienced; inexperienced (in life experience); inexperienced (in love); innocent; insufficient; just a little; life; live (i.e. not recorded); lively; living; me; myself; naive; naivety; native (of); natural; new; not to mention ...; one's initial enthusiasm; one's original intention; opening; origin; outset; partially; perfunctory; produced in; product of; property; pure; raw; sauciness; semi-; simple; somehow; somewhat; start; student; such as ...; tipsiness; uncooked; undiluted; unedited; unpasteurized beer; unpolished; unprocessed; unprotected (sex); unsophisticated; unused; vaguely; virile; vital; wet behind the ears

- **11 written / 13 source entries / 13 readings / 56 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 但 / 只 / 唯 / 徒 / 徒ら / 徒士 / 徒歩 / 歩 / 歩み / 歩行 / 無駄
  - Entries: 1444420, 1444510, 1514290, 1514300, 1514330, 1514370, 1530510, 1538900, 1615140, 2145430, 2249010, 2611940, 2681740
  - Readings: あだ, あゆみ, いたずら, かち, ず, ただ, と, とほ, ふ, ぶ, ほ, ほこう, むだ
  - Deduplicated English glosses: advance; aimless; as is; bu; but; commission; common; company; counter for steps; course (of history, one's life, etc.); development; exactly; except; foot soldier (Edo period); free; free of charge; frivolous; futile; futility; gang; going on foot; history; however; idle; idleness; imprisonment (for 1-3 years; third most severe of the five ritsuryō punishments); just; merely; nevertheless; only; ordinary; pace; party; pawn; percentage; person; pitch (of a screw, etc.); pointlessness; precisely; progress; safe; samurai on foot; set; simply; step; stride; traditional unit of area, approx. 3.31 square metres; transient; unaffected; useless; uselessness; usual; vain; walk; walking; waste

- **10 written / 13 source entries / 17 readings / 44 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 些 / 些か / 小 / 狭 / 細 / 細ら / 細れ / 細小 / 聊 / 聊か
  - Entries: 1610600, 2083540, 2153770, 2268350, 2268360, 2268890, 2268900, 2268910, 2268920, 2268940, 2564200, 2729800, 2729810
  - Readings: いささ, いささか, いさら, お, ぐゎー, こ, さ, さい, さいしょう, ささ, ささら, さざ, さざら, さざれ, しょう, せ, ほそ
  - Deduplicated English glosses: a bit; a little; being almost full; being cramped; dear; detail; details; elementary school; familiar prefix; fine; fine pattern; fine thread; hempen cord; inferior; junior; just about; little; minor; minuscule; minute; narrow; narrowness; pebble; pee; petty; piss; rather; short month (i.e. having fewer than 31 days); slender; slight; slightly; slightly less than; small; small item; smaller (of two things, places, etc. with the same name); smallness; somehow; somewhat; thin; unit of field area (approx. 400 sq m); urination; urine; weak; younger (of two people with the same name)

- **6 written / 12 source entries / 12 readings / 37 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 価 / 値 / 價 / 直 / 費 / 費直
  - Entries: 1430580, 1430590, 1484590, 1581630, 1581790, 1581795, 2188520, 2264640, 2406000, 2406710, 2827787, 2836242
  - Readings: あたい, あたえ, か, じか, じき, ただ, ち, ちょく, なお, ね, ひ, ひた
  - Deduplicated English glosses: Atai (post-Taika reform hereditary title often given to regional administrators); before long; being on duty (in a factory); candid; cash transaction; close; common; cost; direct; doing nothing; earnestly; easygoing; exactly; expense; frank; friendly; honest; immediately; in a moment; level; lighthearted; merit; nearby; night duty; night watch; ordinary; price; shift; shortly; soon; spot transaction; straight; straightforward; valence; valency; value; worth

- **14 written / 11 source entries / 11 readings / 22 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 明かし / 明かり / 明り / 灯 / 灯かり / 灯し / 灯し火 / 灯り / 灯火 / 点火 / 照射 / 燈 / 燈火 / 燭
  - Entries: 1350930, 1441440, 1582290, 1586210, 2519550, 2519560, 2519570, 2519580, 2519590, 2850327, 2850328
  - Readings: あかし, あかり, しょうしゃ, しょく, てんか, とう, とうか, とぼし, ともし, ともしび, ひ
  - Deduplicated English glosses: (a) light; baiting deer with a lighted torch; beaming; counter for electric lights; exposure (to light); firing; gleam; glow; ignition; illumination; irradiation; lamp; lamplight; light; light (esp. as a religious offering); lighting; lights; obsolete unit of luminous intensity (approx. 1.0067 candela); proof (of innocence); radiation; setting off; torch

- **13 written / 11 source entries / 12 readings / 44 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 尽 / 尽く / 悉く / 捗 / 果 / 果たて / 果て / 極 / 極く / 涯 / 涯て / 計 / 量
  - Entries: 1004570, 1192860, 1240180, 1252050, 1554640, 1956100, 2187130, 2270990, 2436470, 2436480, 2817380
  - Readings: か, きょく, けい, ことごとく, ごく, じん, ずく, づく, はか, はたて, はて, りょう
  - Deduplicated English glosses: (in) total; 10^48; altogether; amount; based on (mutual consent, etc.); capacity; climax; consequence; counter for pieces of fruit; culmination; end; enlightenment (as the fruits of one's Buddhist practice); entirely; extreme; extremity; fruit; generosity; height; last day (of the month); limit; magnanimity; measuring device; meter; nadir; outcome; phala (attained state, result); plan; pole; portion (of food); pramana (means by which one gains accurate and valid knowledge; in Indian philosophy); progress; quantity; quindecillion; quite; relying entirely on ...; result; tolerance; total (of); upshot; using solely ...; very; volume; with the sole purpose of ...; zenith

- **12 written / 10 source entries / 12 readings / 30 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: たて糸 / 四 / 恣 / 擅 / 歴 / 竪 / 経 / 経糸 / 縦 / 縦し / 縦糸 / 肆
  - Entries: 1335640, 1579470, 1661350, 1908340, 1956170, 2211960, 2607700, 2747960, 2783730, 2826444
  - Readings: きょう, けい, し, たて, たていと, ふ, ほしいまま, よ, よし, よん, れき, スー
  - Deduplicated English glosses: (weaving) warp; 4; Buddhist scriptures; arbitrary; career; even if; experience; four; front-to-back; height; hierarchy; history; length; longitude; north-to-south; record; scripture; self-indulgent; selfish; sutra; the vertical; to elapse; to experience; to go by; to go through; to pass; to pass through; trans-; vertical (relationship); warp (weaving)

- **12 written / 10 source entries / 10 readings / 55 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 倭 / 凪 / 和 / 和ぎ / 和ら / 大和 / 暇 / 柔 / 柔ら / 軟ら / 遑 / 閑
  - Entries: 1459650, 1561920, 1577280, 1585330, 2039460, 2437530, 2709280, 2763050, 2812930, 2858986
  - Readings: いとま, かん, じゅう, なぎ, のど, ひま, やまと, やわ, やわら, わ
  - Deduplicated English glosses: (ancient) Japan; (spare) time; Japan; Japanese-style; Yamato (battleship); Yamato (former province located in present-day Nara Prefecture); Yamato kingdom; calm; calm (at sea); dismissal; divorce; dull; farewell; firing; fragile; free; free time; gentleness; harmony; holiday; idle; insubstantial; judo; jujutsu; leave; leaving; leisure; lull; not busy; peace; peaceful; poorly built; quiet; resignation; severance; slack; slow (business); soft; softness; spare time; state of Yamato; sum; taking one's leave; time (it takes to do something); time off; time required; to be available; to be doing nothing; to be free; to be not busy; tranquil; unoccupied; vacation; weak; weakness

- **10 written / 10 source entries / 15 readings / 19 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: シカ肉 / 猪 / 猪の子 / 猪子 / 獣 / 豕 / 豚 / 豬 / 鹿 / 鹿肉
  - Entries: 1319030, 1335590, 1427010, 1457390, 2073740, 2203180, 2203220, 2203230, 2203240, 2763370
  - Readings: い, いのこ, いのしし, か, かせぎ, かのしし, けだもの, けもの, しか, しかにく, しし, じゅう, とん, ぶた, ろく
  - Deduplicated English glosses: animal; beast; beast (esp. one used for its meat, such as a boar or a deer); brute; cervid; deer; deer (esp. the sika deer, Cervus nippon); deer meat; fatso; fatty; hog (esp. a wild boar, but also including the domesticated pig); hunting (of animals such as boar, deer, etc.); infant wild boar; pig; pig (Sus scrofa domesticus); pork; venison; wild boar; wild pig

- **10 written / 10 source entries / 15 readings / 24 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 両 / 二 / 双 / 弍 / 弐 / 諸 / 諸々 / 諸諸 / 貮 / 貳
  - Entries: 1344210, 1398670, 1461140, 1605340, 2080720, 2701150, 2728160, 2832843, 2858788, 2859856
  - Readings: しょ, そう, に, ふ, ふう, ふた, もろ, もろもろ, りゃん, りょう, アル, アー, アール, テール, リャン
  - Deduplicated English glosses: 2; all; all kinds of things; both; both (hands, parents, sides, etc.); counter for carriages (e.g. in a train); counter for suits of clothing, sets of armor, etc.; counter for vehicles; large number of people; many; pair; pre-Meiji unit of currency, orig. the value of one ryō of gold; ryō; samurai; several; tael; tael (Chinese unit of weight and currency); together; traditional measure of fabric, 2 tan; traditional unit of weight (for gold, silver and drugs), 4-5 monme, 15-19 g; two; unit of weight under the ritsuryō system, 1/16 kin, 42-43 g; various; various things

- **5 written / 10 source entries / 13 readings / 35 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 下部 / 僕 / 奴 / 臣 / 隷
  - Entries: 1186270, 1445630, 1445640, 1521390, 1521400, 2055600, 2089650, 2228300, 2228350, 2844147
  - Readings: おみ, かぶ, しもべ, しん, つぶね, ぼく, め, やっこ, やつ, やつがれ, やつこ, やつこらま, れい
  - Deduplicated English glosses: Edo-period hairstyle worn by samurai's attendants; I; Omi (hereditary title; orig. one of the two highest such titles, later demoted to sixth highest of eight); attendant; bastard; captive; chap; chivalrous man (Edo period); clerical script (ancient, highly angular style of kanji); cubed tofu (often served cold); damn; enslavement (of a woman; Edo-period punishment for her own or her husband's crime); fellow; good and faithful servant; guy; he; her; him; humble; kite shaped like an Edo-period footman; lower part; manservant; me; menial; object; retainer; servant; servant (esp. a samurai's attendant); she; slave; subordinate (office); substructure; thing; varlet; you

- **5 written / 10 source entries / 15 readings / 30 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 神 / 霊 / 魂 / 魄 / 魑魅
  - Entries: 1364440, 1557730, 1579170, 1893380, 2177290, 2177300, 2433050, 2736430, 2836999, 2844111
  - Readings: かみ, かむ, かん, こん, しん, じん, すだま, たま, たましい, ちみ, はく, み, りょう, れい, ろう
  - Deduplicated English glosses: Emperor (of Japan); Yang energy; Yin energy; amazing; deity; departed soul; divine; divine spirit; divinity; fantastic; ghost; god; god-tier; godly; incredible; inherent spirit of something; kami; mind; mountain demon; psyche; revengeful ghost; something as important as one's own life; something invaluable; soul; spirit; state of mind; thunder; vengeful spirit; vitality; willpower

- **18 written / 9 source entries / 9 readings / 12 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 一位 / 団栗 / 杼 / 栃 / 栃の木 / 梭 / 椚 / 椢 / 槶 / 橡 / 橡の木 / 櫟 / 櫪 / 石櫧 / 禾予 / 赤檮 / １位 / 𥝱
  - Entries: 1161020, 1419200, 1922090, 2066140, 2127910, 2145440, 2264890, 2723840, 2853458
  - Readings: いちい, くぬぎ, くのぎ, じょ, つるばみ, とち, とちのき, どんぐり, ひ
  - Deduplicated English glosses: 10^24; Japanese horse chestnut (Aesculus turbinata); Japanese yew (Taxus cuspidata); Quercus gilva (species of oak); acorn; dark gray; dark grey; first place; first rank; sawtooth oak (Quercus acutissima); shuttle (of a loom); units position (of a number)

- **6 written / 9 source entries / 11 readings / 55 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 下 / 下ル / 許 / 許し / 許り / 赦し
  - Entries: 1010240, 1184140, 1232860, 2004390, 2080200, 2080210, 2159100, 2256550, 2867124
  - Readings: か, げ, さがる, した, しも, ばかし, ばかり, ばっかし, ばっかり, もと, ゆるし
  - Deduplicated English glosses: (as though) about to; (someone's) location; (someone's) side; about; always; approximately; as if to; at any minute; below; beneath; bottom; constantly; dirty (e.g. dirty jokes, etc.); down; during (war, occupation, etc.); end; exemption; far from the imperial palace (i.e. far from Kyoto, esp. of western Japan); feces (faeces); forgiveness; going to; in (e.g. the name of ...); indicates emphasis; inferiority; just (finished, etc.); just after; lower half (of the body, esp. the privates); lower part; lower reaches (of a river); lowness (of degree, value, etc.); menses; merely; no more than; nothing but; on (the promise, condition, assumption, etc. that ...); on the point of; one's inferior (i.e. one's junior); only; pardon; permission; preliminary; preparatory; right after; second volume (of two); south of; third volume (of three); trade-in; under; under (a flag, the sun, etc.); under (guidance, supervision, rules, the law, etc.); under (influence, control, conditions, etc. of); underneath; urine; with (e.g. one blow); younger (e.g. daughter)

- **5 written / 9 source entries / 10 readings / 46 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 咎 / 品 / 段階 / 科 / 階
  - Entries: 1192680, 1203020, 1419950, 1583470, 2543170, 2611400, 2648750, 2648780, 2836179
  - Readings: か, かい, きざはし, きだはし, しな, だんかい, とが, はし, ひん, ほん
  - Deduplicated English glosses: Japanese linden (Tilia japonica); article; branch of study; chapter; class; coquetry; counter for dishes or courses (at a restaurant); counter for items (of food, etc.); counter for storeys and floors of a building; course (of study); court rank; department (of a university or hospital); dignity; elegance; error; family; fault; flirtatiousness; floor; goods; grace; gradation; grade; item; level; mistake; offense; order; phase; quality; rank; refinement; section; sin; specialization; stage; stage (in chronostratigraphy); stairs; stairs at the front of a noh stage; step; stock; storey; story; thing; volume; wrongdoing

- **5 written / 9 source entries / 9 readings / 32 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 儕 / 原 / 族 / 親族 / 輩
  - Entries: 1261130, 1261140, 1365270, 1405770, 1472960, 1472965, 1472970, 2178790, 2820740
  - Readings: うから, げん, しんぞく, ぞく, ともがら, はい, はら, ばら, やから
  - Deduplicated English glosses: blood relative; bunch; clan; comrade; family; fellow; field; fundamental; gang; group; group (in the periodic table); group (of people who share a common feature); kin; lot; moor; original; party (of people); people; plain; prairie; primary; primitive; proto-; race; relation; relative; set (of people); suffixed to words indicating a class of people to create a plural (esp. in impolite contexts); tribe; tribe (taxonomic rank); tundra; wilderness

- **12 written / 8 source entries / 8 readings / 2 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 不如帰 / 子規 / 時鳥 / 杜宇 / 杜鵑 / 沓手鳥 / 沓直鳥 / 田鵑 / 蜀魂 / 蜀魄 / 郭公 / 霍公鳥
  - Entries: 1206270, 1444570, 2861877, 2861878, 2861879, 2861880, 2861881, 2861882
  - Readings: かっこう, くつてどり, しき, しょっこん, とう, とけん, ふじょき, ほととぎす
  - Deduplicated English glosses: common cuckoo (Cuculus canorus); lesser cuckoo (Cuculus poliocephalus)

- **10 written / 8 source entries / 11 readings / 29 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 杪 / 梢 / 楉 / 楚 / 笞 / 策 / 細枝 / 葼 / 鞭 / 韃
  - Entries: 1298260, 1349980, 1633970, 2246420, 2249050, 2249060, 2249070, 2853614
  - Readings: こずえ, さく, しもと, すわい, すわえ, ずわい, ずわえ, ち, ぶち, ほそえだ, むち
  - Deduplicated English glosses: cane; caning (10-50 strokes; least severe of the five ritsuryō punishments); encouragement; fifth principle of the Eight Principles of Yong; goad; lash; means; measure; plan; pointer; policy; rebuke; right upward flick; rod; scheme; scion; scourge; slender shoot; spur; stick; stratagem; switch; switch (cane used for flogging); switch (long, slender shoot of a tree); tip of a branch; treetop; twig; wand; whip

- **8 written / 8 source entries / 8 readings / 49 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 弾 / 梂 / 毬 / 玉 / 珠 / 球 / 璧 / 鞠
  - Entries: 1226280, 1229880, 1240530, 1921990, 2016130, 2179300, 2529890, 2735950
  - Readings: いが, かさ, きゅう, ぎょく, たま, だん, へき, まり
  - Deduplicated English glosses: ball; ball (for sport, games, etc.); ball (i.e. a testicle); ball (in sports); bead (of an abacus); bead (of sweat, dew, etc.); beautiful; bi (ancient Chinese artifact; flat jade or glass disc with a circular hole in the centre); bulb; bulb (i.e. a light bulb); bullet; bur; burr (e.g. chestnut); case; character; coin; cone (e.g. pine, hemlock); counter for bullets; counter for parts, stages, installments, etc. (of a story, series, project, campaign, etc.); counter for pitches; cupule; drop; droplet; egg; egg (esp. as a sushi topping); excellent; female entertainer (e.g. a geisha); geisha; gem; globe; item, funds or person used as part of a plot; jewel (esp. spherical; sometimes used figuratively); kemari; king (of the junior player); lens (of glasses, etc.); okonomiyaki; orb; pearl; person (when commenting on their nature); pile (of noodles, etc.); pitch (e.g. in baseball); position (in finance, the amount of a security either owned or owed by an investor or dealer); precious; precious stone (esp. jade); product being bought or sold; sphere; stock or security being traded; time charge for a geisha; type of football played by courtiers in ancient Japan

- **7 written / 8 source entries / 8 readings / 44 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 七五三 / 印 / 導 / 標 / 注連 / 証 / 証し
  - Entries: 1168050, 1168060, 1319310, 1351580, 2564050, 2619980, 2843869, 2859158
  - Readings: あかし, いん, しちごさん, しめ, しょう, しるし, しるべ, ひょう
  - Deduplicated English glosses: 7-5-3 day; ID card; India; badge; certificate; chop; cordoning off; cordoning-off sign; crest; emblem; enlightenment; evidence; festival day for children aged 7, 5 and 3 held on or around November 15; flag; guidance; guide; license; lucky numbers 7, 5 and 3; mark; membership card; mudra (symbolic hand gesture); nameplate; ninja hand sign; patient's condition; permit; plain wood showing the seating order of officials at court; print; proof; rope used to cordon off consecrated areas or as a talisman against evil; seal; seal impression; sealing; shichi-go-san; sign; stamp; symbol; symptoms (in Chinese medicine); target; testament (to something); testifying; testimony; token (of gratitude, affection, etc.); vindication; witnessing

- **7 written / 8 source entries / 10 readings / 42 glosses** — Подозрительно как единый lexical word: много разнородных written/readings/senses.
  - Written: 暈 / 曲 / 殃 / 災い / 禍 / 阿 / 隈
  - Entries: 1239700, 1295080, 1957130, 2131810, 2261020, 2589750, 2844158, 2844412
  - Readings: あ, うん, か, かさ, きょく, くせ, くま, まが, わざわい, クマ
  - Deduplicated English glosses: Africa; Awa (old province of Japan); air; bend (in a road, river, etc.); calamity; composition; corner; corona; dark area; dark circles (under the eyes); dark rings; disaster; enjoyment; evil; first Sanskrit alphabet letter; fun; gradation; halo (around the Sun, Moon, etc.); improper; indecent; interest; kumadori; long segment of a noh play forming its musical highlight; melody; misfortune; nook; piece of music; pleasure; prefixed to names to show intimacy; recess; ring; shade; shading; shadow; song; style of kabuki makeup used for violent roles; track (on a record); trouble; tune; wickedness; woes; wrong

Логично на уровне source grouping: single-entry 倶利伽羅紋紋 group (17 orthographic forms, 1 reading, 2 glosses). Относительно правдоподобны 学びや/学び舎/学舎, но cross-entry がくしゃ и まなびや всё равно требуют сохранения provenance.

## 12. Word Details impact

| Written forms per C group | Groups |
| --- | --- |
| 1 | 136 856 |
| 2 | 23 079 |
| 3 | 6 858 |
| 4 | 2 832 |
| 5+ | 1 960 |
| Maximum | 48 |

Alternative block нужен 34 729 groups, **20.24%**. Alternatives = written_count−1: >3 alternatives 1 960; >5 442; >10 39. Maximum 47 alternatives.

Distribution readings per word group:
| Значение | Количество |
| --- | --- |
| 1 | 159 858 |
| 2 | 9 639 |
| 3 | 1 372 |
| 4 | 423 |
| 5 | 138 |
| 6 | 58 |
| 7 | 27 |
| 8 | 30 |
| 9 | 10 |
| 10 | 6 |
| 11 | 4 |
| 12 | 3 |
| 13 | 3 |
| 15 | 4 |
| 16 | 2 |
| 17 | 1 |
| 24 | 1 |
| 26 | 1 |
| 29 | 1 |
| 33 | 1 |
| 35 | 1 |
| 44 | 1 |
| 77 | 1 |

Distribution deduplicated English gloss strings per group (not linguistic senses):
| Значение | Количество |
| --- | --- |
| 1 | 82 250 |
| 2 | 47 487 |
| 3 | 20 258 |
| 4 | 9 319 |
| 5 | 4 645 |
| 6 | 2 621 |
| 7 | 1 504 |
| 8 | 944 |
| 9 | 610 |
| 10 | 476 |
| 11 | 310 |
| 12 | 234 |
| 13 | 149 |
| 14 | 136 |
| 15 | 104 |
| 16 | 66 |
| 17 | 81 |
| 18 | 48 |
| 19 | 53 |
| 20 | 33 |
| 21 | 22 |
| 22 | 22 |
| 23 | 19 |
| 24 | 22 |
| 25 | 15 |
| 26 | 13 |
| 27 | 4 |
| 28 | 13 |
| 29 | 10 |
| 30 | 8 |
| 31 | 9 |
| 32 | 7 |
| 33 | 8 |
| 34 | 5 |
| 35 | 4 |
| 36 | 6 |
| 37 | 4 |
| 38 | 6 |
| 39 | 1 |
| 40 | 1 |
| 41 | 2 |
| 42 | 3 |
| 43 | 2 |
| 44 | 4 |
| 45 | 1 |
| 46 | 2 |
| 47 | 5 |
| 48 | 4 |
| 49 | 3 |
| 51 | 1 |
| 52 | 1 |
| 53 | 3 |
| 54 | 1 |
| 55 | 2 |
| 56 | 1 |
| 57 | 1 |
| 59 | 2 |
| 62 | 1 |
| 63 | 2 |
| 64 | 1 |
| 68 | 2 |
| 69 | 1 |
| 74 | 3 |
| 75 | 1 |
| 76 | 1 |
| 84 | 1 |
| 89 | 1 |
| 91 | 1 |
| 121 | 1 |
| 151 | 1 |
| 160 | 1 |
| 178 | 1 |
| 213 | 1 |

Distribution group maximum written length:
| Значение | Количество |
| --- | --- |
| 1 | 1 613 |
| 2 | 48 235 |
| 3 | 41 624 |
| 4 | 38 282 |
| 5 | 18 315 |
| 6 | 11 005 |
| 7 | 5 050 |
| 8 | 3 247 |
| 9 | 1 696 |
| 10 | 1 040 |
| 11 | 624 |
| 12 | 369 |
| 13 | 186 |
| 14 | 109 |
| 15 | 81 |
| 16 | 48 |
| 17 | 17 |
| 18 | 16 |
| 19 | 8 |
| 20 | 9 |
| 21 | 3 |
| 22 | 5 |
| 23 | 1 |
| 24 | 1 |
| 27 | 1 |

Displayed-form lengths внутри C groups с alternatives:
| Значение | Количество |
| --- | --- |
| 1 | 2 049 |
| 2 | 21 135 |
| 3 | 22 561 |
| 4 | 20 622 |
| 5 | 12 276 |
| 6 | 5 731 |
| 7 | 2 392 |
| 8 | 1 235 |
| 9 | 702 |
| 10 | 482 |
| 11 | 322 |
| 12 | 184 |
| 13 | 115 |
| 14 | 63 |
| 15 | 46 |
| 16 | 35 |
| 17 | 9 |
| 18 | 7 |
| 19 | 7 |
| 20 | 3 |
| 21 | 1 |
| 22 | 1 |
| 26 | 1 |
| 27 | 1 |

Это distribution по всем possible written forms, не прогноз реально выбранного default displayed form: canonical selection новой модели пока не определена. Текущий UI compact ≤5, длинные >5; maximum 77 readings и 213 unique gloss strings. Extreme examples section 10/11 показывают необходимость bounded/expandable representations и sense/readings applicability; это observations, не UI implementation plan.

## 13. Current runtime identity dependencies

| Место | Current assumption | Impact word_id / слой изменений |
| --- | --- | --- |
| DictionaryDatabase.kt: DictionaryWord/Details, getWord | entryId+written; readings/meanings selected by one source entry | SQL и Kotlin models; сохранить mapping written→reading→sense. Не только dictionary-side. |
| WordSearch.kt / WordSearchRelevance.kt | candidate SQL/maps key (entry_id,written); earliest reading_order per spelling; entry_id tie-break | SQL identity, ranking metadata, pagination/hasMore counts и result DTOs. |
| Explore Words SQL | 5 random distinct entry_ids, one common representative per entry | Sampling identity/canonical representative меняются; dictionary+runtime. |
| Word Details screen | remember/LaunchedEffect(entryId,written,sourceKanji); one displayed written; readings only for it | Parameters/cache keys/models/alternative availability; UI/runtime change. |
| RelatedWordsRanker / WordQuality | distinct(entryId,written), representative same entry+reading, source priority/order | Candidate identity, representative selection, tie-break; runtime. |
| WordTrainingCandidates / WordTrainingSelection | SQL and glossary maps (entry,written); distinct written; one primary reading/gloss | Training selection/dedup eligibility/coverage must choose actual written-specific realization. |
| WordTrainingSession / Results | key Pair<Long,String>; lastResults, repeat sets and list keys use entry+written | Result identity semantics must be reconsidered: word-level vs displayed-form-level; Kotlin/state/UI. |
| KanjiDbApp.kt navigation | word/{entryId}/{written}?sourceKanji=... | Route arguments, callbacks and detail return chain/runtime state. |
| Search/Related/Results Lazy list keys | entryId:written | Stable UI keys change, cache/restoration semantics need review. |
| Room user.db | Unicode kanji characters, list IDs; no persistent word IDs | No currently required user Room migration for word_id; dictionary rebuild alone does not update runtime contracts. |

Word sessions/search result DTOs are process-owned rather than persisted word-history tables. Existing entry_id source identity is spread across SQL, model keys, sorting, navigation and Results; replacing just dictionary schema will break those contracts.

## 14. Existing presentation-level deduplication

| Mechanism | Key/conditions | Cross-entry? | After redesign |
| --- | --- | --- | --- |
| groupCommonWords / deduplicateCommonWords | same entryId+selected reading; normalized full English gloss sets overlap≥75% of larger; retained representatives only | No | Likely superseded for explicit same-entry variants; exact reading guard conflicts with broader variants. Review, do not keep twice blindly. |
| Search groupWordSearchResults | loaded page, equal normalized primaryMeaning; ordered code-point subsequence either direction; every pair compatible | Yes; identical written qualifies | Explicit variants may replace part; it can currently group independent sources/homonyms. Preserve ranking/each-form click semantics deliberately. |
| Search SQL / results map | GROUP BY entry,written; best matched gloss per candidate | No | Still need one display hit and ranking over applicable senses/readings, identity key changes. |
| Word Details meanings | GROUP BY language,TRIM(meaning), across readings of selected entry,written; orderedWordMeanings distinct | No | Exact display-string dedup may remain useful; should not erase sense restrictions. |
| Word Details constituent kanji | GROUP BY kanji id; MIN(position) | Not lexical grouping | Still useful for repeated kanji in displayed spelling. |
| Related Words candidate SQL | GROUP BY entry,written; earliest reading; MAX common/priority; selected reading glosses | No | Candidate aggregation rewritten; ranking is independent of lexical dedup. |
| Training gloss query | GROUP BY selected id,language,TRIM(meaning); aggregates all valid readings of displayed written | No | Display-gloss dedup can remain; applicability/model changes required. |
| Training final distinctBy written | Keeps first ranked exact-written candidate after equivalence pass | Yes | Model B may make redundant, but form-level training behavior must remain explicit. |
| CounterConstructions suppression | exact numeral 1..99 or decimal digits + 本/人/個/枚/匹/台/冊/回/階; one family representative | Yes | Pedagogical/display suppression, not lexical identity; normalized schema does not automatically obsolete it. |
| distinctBy entry+written / mergeKanjiWords / chunks | Removes repeated fetches of same display identity | No | Query-result dedup remains possible; identity key changes. |

Existing presentation logic уже прячет часть database repetition. 75% pass — non-transitive representative grouping; Model C deliberately transitive and much broader. Это разные semantics.

## 15. Performance / DB-size observations

Current DB: 144 142 336 bytes = 137.46 MiB; 35 191 pages, 14 freelist pages. Total current word rows = 1 448 666. `dbstat` unavailable in local Python SQLite, so precise per-table/index byte attribution is not claimed.

| Illustrative normalized payload | Count | Caveat |
| --- | --- | --- |
| word groups C | 171 585 | Diagnostic identity, not approved model |
| distinct written | 226 836 | Global written links, C partitions each exact written into one component |
| group-reading links | 187 057 | Still need written↔reading applicability |
| source sense records | 204 381 | Distinct imported English-bearing (entry,sense_index) |
| source sense/gloss records | 371 578 | Dedup repeated copies across forms; preserves source/sense identity |
| flat group unique gloss strings | 368 267 | Display lower payload; not enough for lossless sense storage |
| written-kanji-position links | 581 846 | Dedup repeats across readings and source entries |

Potential gloss storage: 583 285 current copies -> 371 578 source-sense/gloss records (−36.30%); kanji links 619 220 -> 581 846 (−6.04%). Это не окончательная schema estimate.

Splitting word/written/reading/sense/meaning/source mapping может **увеличить суммарный count rows**, даже уменьшая duplicated text. Existing valid written-reading associations остаются примерно 246k и будут отдельными links; потребуются sense restriction/source mappings и indexes. Flat C group/gloss UNION потеряет applicability. Уменьшение identities на 25.8% не означает уменьшение DB bytes на 25.8%; требуется отдельный lossless prototype вне production DB для byte/performance сравнения. Анализ завершился примерно за 77.96 seconds; это desktop research timing, не Android startup/query benchmark.

## 16. Recommendation for what we should investigate next

1. Сначала разобрать exact-link paths крупнейших C components и различие visual written identity / lexical entry identity. Exact-written visual merge может быть полезен, но его транзитивное распространение на все same-entry variants даёт смешанные группы.
2. Проверить preservation matrix `written ↔ reading ↔ sense ↔ source` на реальных re_restr/stagk/stagr cases. Gloss-string dedup не может заменять sense model.
3. Вручную разметить stratified sample cross-entry candidates: exact/near reading, high/low gloss overlap, короткие/длинные, proper names. Даже overlap=1 и reading distance=0 дают кандидаты с потенциально разными referents.
4. Уточнить, что оценивается в Training/Results: lexical word или конкретный displayed written с конкретным reading/sense. Сейчас scoring identity и coverage tied to displayed spelling.
5. Только после этих исследований измерить отдельный lossless normalized prototype и сверить запросы/size. Production heuristic cross-entry merge сейчас не обоснован.

## Артефакты и неизменность

Сохранены `jmdict_report.py`, `render_report.py`, `report.json`, `report.md` в tools/diagnostics для воспроизводимости и review. Они не подключены к build pipeline и не входят в assets. Временный run.log удалён после завершения.

SHA-256 inputs:
| Input | SHA-256 |
| --- | --- |
| app\src\main\assets\dictionary.db | 3ddb3ec15830ae082cfcab4883d2632f14e30b618e9b55691ec43ddee3b7480f |
| tools\data\jmdict.xml | 5bc238e7e9775ac5968b6b03931fa10b6aa4879122da1f8fbba84c492cbcd44d |

Версии без изменений: app `v0.9.0`, versionCode `46`, dictionary `1`. Build/device/UI tests не запускались; для read-only диагностики они не требуются.
