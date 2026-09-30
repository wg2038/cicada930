package dev.x.opusone.data.model

data class ClassicalQuote(
    val quote: String,
    val source: String,
    val searchKeyword: String,
    val chapterId: Int = 1
)

val CLASSICAL_QUOTES = listOf(
    ClassicalQuote(
        quote = "劳身焦思，居外十三年，过家门不敢入。",
        source = "《史记 · 夏本纪》",
        searchKeyword = "劳身焦思",
        chapterId = 2
    ),
    ClassicalQuote(
        quote = "防民之口，甚于防川。",
        source = "《史记 · 周本纪》",
        searchKeyword = "防民之口",
        chapterId = 4
    ),
    ClassicalQuote(
        quote = "一法度衡石丈尺。车同轨。书同文字。",
        source = "《史记 · 秦始皇本纪》",
        searchKeyword = "车同轨",
        chapterId = 6
    ),
    ClassicalQuote(
        quote = "仁义不施而攻守之势异也。",
        source = "《史记 · 秦始皇本纪》",
        searchKeyword = "仁义不施",
        chapterId = 6
    ),
    ClassicalQuote(
        quote = "大行不顾细谨，大礼不辞小让。",
        source = "《史记 · 项羽本纪》",
        searchKeyword = "大行不顾细谨",
        chapterId = 7
    ),
    ClassicalQuote(
        quote = "今者项庄拔剑舞，其意常在沛公也。",
        source = "《史记 · 项羽本纪》",
        searchKeyword = "其意常在沛公",
        chapterId = 7
    ),
    ClassicalQuote(
        quote = "彼可取而代也。",
        source = "《史记 · 项羽本纪》",
        searchKeyword = "彼可取而代也",
        chapterId = 7
    ),
    ClassicalQuote(
        quote = "书足以记名姓而已。剑一人敌，不足学，学万人敌。",
        source = "《史记 · 项羽本纪》",
        searchKeyword = "学万人敌",
        chapterId = 7
    ),
    ClassicalQuote(
        quote = "运筹策帷帐之中，决胜于千里之外。",
        source = "《史记 · 高祖本纪》",
        searchKeyword = "运筹策帷帐",
        chapterId = 8
    ),
    ClassicalQuote(
        quote = "嗟乎，大丈夫当如此也！",
        source = "《史记 · 高祖本纪》",
        searchKeyword = "大丈夫当如此",
        chapterId = 8
    ),
    ClassicalQuote(
        quote = "大风起兮云飞扬，威加海内兮归故乡，安得猛士兮守四方！",
        source = "《史记 · 高祖本纪》",
        searchKeyword = "大风起兮云飞扬",
        chapterId = 8
    ),
    ClassicalQuote(
        quote = "与父老约，法三章耳：杀人者死，伤人及盗抵罪。",
        source = "《史记 · 高祖本纪》",
        searchKeyword = "法三章耳",
        chapterId = 8
    ),
    ClassicalQuote(
        quote = "吾一沐三捉发，一饭三吐哺，起以待士，犹恐失天下之贤人。",
        source = "《史记 · 鲁周公世家》",
        searchKeyword = "一沐三捉发",
        chapterId = 33
    ),
    ClassicalQuote(
        quote = "前事之不忘，后事之师也。",
        source = "《史记 · 赵世家》",
        searchKeyword = "前事之不忘",
        chapterId = 43
    ),
    ClassicalQuote(
        quote = "飞鸟尽，良弓藏；狡兔死，走狗烹。",
        source = "《史记 · 越王勾践世家》",
        searchKeyword = "狡兔死",
        chapterId = 41
    ),
    ClassicalQuote(
        quote = "苦身焦思，置胆于坐，坐卧即仰胆，饮食亦尝胆也。",
        source = "《史记 · 越王勾践世家》",
        searchKeyword = "饮食亦尝胆",
        chapterId = 41
    ),
    ClassicalQuote(
        quote = "高山仰止，景行行止。虽不能至，然心向往之。",
        source = "《史记 · 孔子世家》",
        searchKeyword = "高山仰止",
        chapterId = 47
    ),
    ClassicalQuote(
        quote = "读《易》，韦编三绝。",
        source = "《史记 · 孔子世家》",
        searchKeyword = "韦编三绝",
        chapterId = 47
    ),
    ClassicalQuote(
        quote = "燕雀安知鸿鹄之志哉！",
        source = "《史记 · 陈涉世家》",
        searchKeyword = "燕雀安知",
        chapterId = 48
    ),
    ClassicalQuote(
        quote = "王侯将相宁有种乎！",
        source = "《史记 · 陈涉世家》",
        searchKeyword = "王侯将相",
        chapterId = 48
    ),
    ClassicalQuote(
        quote = "苟富贵，无相忘。",
        source = "《史记 · 陈涉世家》",
        searchKeyword = "苟富贵",
        chapterId = 48
    ),
    ClassicalQuote(
        quote = "萧何为法，顜若画一；曹参代之，守而勿失。",
        source = "《史记 · 曹相国世家》",
        searchKeyword = "萧何为法",
        chapterId = 54
    ),
    ClassicalQuote(
        quote = "忠言逆耳利于行，毒药苦口利于病。",
        source = "《史记 · 留侯世家》",
        searchKeyword = "忠言逆耳",
        chapterId = 55
    ),
    ClassicalQuote(
        quote = "孺子可教矣。",
        source = "《史记 · 留侯世家》",
        searchKeyword = "孺子可教",
        chapterId = 55
    ),
    ClassicalQuote(
        quote = "嗟乎，使平得宰天下，亦如是肉矣！",
        source = "《史记 · 陈丞相世家》",
        searchKeyword = "使平得宰天下",
        chapterId = 56
    ),
    ClassicalQuote(
        quote = "军中闻将军之令，不闻天子之诏。",
        source = "《史记 · 绛侯周勃世家》",
        searchKeyword = "不闻天子之诏",
        chapterId = 57
    ),
    ClassicalQuote(
        quote = "傥所谓天道，是邪非邪？",
        source = "《史记 · 伯夷列传》",
        searchKeyword = "傥所谓天道",
        chapterId = 61
    ),
    ClassicalQuote(
        quote = "岁寒，然后知松柏之后凋。",
        source = "《史记 · 伯夷列传》",
        searchKeyword = "岁寒",
        chapterId = 61
    ),
    ClassicalQuote(
        quote = "生我者父母，知我者鲍子也。",
        source = "《史记 · 管晏列传》",
        searchKeyword = "生我者父母",
        chapterId = 62
    ),
    ClassicalQuote(
        quote = "仓廪实而知礼节，衣食足而知荣辱。",
        source = "《史记 · 管晏列传》",
        searchKeyword = "仓廪实而知礼节",
        chapterId = 62
    ),
    ClassicalQuote(
        quote = "良贾深藏若虚，君子盛德，容貌若愚。",
        source = "《史记 · 老子韩非列传》",
        searchKeyword = "良贾深藏若虚",
        chapterId = 63
    ),
    ClassicalQuote(
        quote = "将在军，君令有所不受。",
        source = "《史记 · 司马穰苴列传》",
        searchKeyword = "君令有所不受",
        chapterId = 64
    ),
    ClassicalQuote(
        quote = "批亢捣虚，形格势禁，则自为解耳。",
        source = "《史记 · 孙子吴起列传》",
        searchKeyword = "批亢捣虚",
        chapterId = 65
    ),
    ClassicalQuote(
        quote = "吾日暮途远，吾故倒行而逆施之。",
        source = "《史记 · 伍子胥列传》",
        searchKeyword = "倒行而逆施",
        chapterId = 66
    ),
    ClassicalQuote(
        quote = "尺有所短，寸有所长。",
        source = "《史记 · 白起王翦列传》",
        searchKeyword = "尺有所短",
        chapterId = 73
    ),
    ClassicalQuote(
        quote = "生者必有死，物之必至也；富贵多士，贫贱寡友，事之固然也。",
        source = "《史记 · 孟尝君列传》",
        searchKeyword = "富贵多士，贫贱寡友",
        chapterId = 75
    ),
    ClassicalQuote(
        quote = "长铗归来乎，食无鱼。",
        source = "《史记 · 孟尝君列传》",
        searchKeyword = "长铗归来乎",
        chapterId = 75
    ),
    ClassicalQuote(
        quote = "夫贤士之处世也，譬若锥之处囊中，其颖脱而出矣。",
        source = "《史记 · 平原君虞卿列传》",
        searchKeyword = "锥之处囊中",
        chapterId = 76
    ),
    ClassicalQuote(
        quote = "夫人有德于公子，公子不可忘也；公子有德于人，愿公子忘之也。",
        source = "《史记 · 魏公子列传》",
        searchKeyword = "公子不可忘也",
        chapterId = 77
    ),
    ClassicalQuote(
        quote = "物盛则衰，天地之常数也；进退盈缩，与时变化，圣人之常道也。",
        source = "《史记 · 范雎蔡泽列传》",
        searchKeyword = "物盛则衰",
        chapterId = 79
    ),
    ClassicalQuote(
        quote = "善作者不必善成，善始者不必善终。",
        source = "《史记 · 乐毅列传》",
        searchKeyword = "善作者不必善成",
        chapterId = 80
    ),
    ClassicalQuote(
        quote = "城入赵而璧留秦；城不入，臣请完璧归赵。",
        source = "《史记 · 廉颇蔺相如列传》",
        searchKeyword = "完璧归赵",
        chapterId = 81
    ),
    ClassicalQuote(
        quote = "相如持璧，倚柱，怒发上冲冠。",
        source = "《史记 · 廉颇蔺相如列传》",
        searchKeyword = "怒发上冲冠",
        chapterId = 81
    ),
    ClassicalQuote(
        quote = "吾所以为此者，以先国家之急而后私仇也。",
        source = "《史记 · 廉颇蔺相如列传》",
        searchKeyword = "先国家之急",
        chapterId = 81
    ),
    ClassicalQuote(
        quote = "卒相与欢，为刎颈之交。",
        source = "《史记 · 廉颇蔺相如列传》",
        searchKeyword = "为刎颈之交",
        chapterId = 81
    ),
    ClassicalQuote(
        quote = "所贵于天下之士者，为人排患、释难、解纷乱而无所取也。",
        source = "《史记 · 鲁仲连邹阳列传》",
        searchKeyword = "为人排患",
        chapterId = 83
    ),
    ClassicalQuote(
        quote = "举世混浊而我独清，众人皆醉而我独醒。",
        source = "《史记 · 屈原贾生列传》",
        searchKeyword = "举世混浊",
        chapterId = 84
    ),
    ClassicalQuote(
        quote = "皭然泥而不滓者也。推此志也，虽与日月争光可也。",
        source = "《史记 · 屈原贾生列传》",
        searchKeyword = "泥而不滓",
        chapterId = 84
    ),
    ClassicalQuote(
        quote = "风萧萧兮易水寒，壮士一去兮不复还！",
        source = "《史记 · 刺客列传》",
        searchKeyword = "风萧萧兮",
        chapterId = 86
    ),
    ClassicalQuote(
        quote = "士为知己者死，女为说己者容。",
        source = "《史记 · 刺客列传》",
        searchKeyword = "士为知己者死",
        chapterId = 86
    ),
    ClassicalQuote(
        quote = "众人遇我，我故众人报之；国士遇我，我故国士报之。",
        source = "《史记 · 刺客列传》",
        searchKeyword = "国士遇我",
        chapterId = 86
    ),
    ClassicalQuote(
        quote = "图穷而匕首见。",
        source = "《史记 · 刺客列传》",
        searchKeyword = "图穷而匕首见",
        chapterId = 86
    ),
    ClassicalQuote(
        quote = "太山不让土壤，故能成其大；河海不择细流，故能就其深。",
        source = "《史记 · 李斯列传》",
        searchKeyword = "太山不让土壤",
        chapterId = 87
    ),
    ClassicalQuote(
        quote = "吾欲与若复牵黄犬俱出上蔡东门逐狡兔，岂可得乎！",
        source = "《史记 · 李斯列传》",
        searchKeyword = "上蔡东门逐狡兔",
        chapterId = 87
    ),
    ClassicalQuote(
        quote = "治世不一道，便国不法古。",
        source = "《史记 · 商君列传》",
        searchKeyword = "治世不一道",
        chapterId = 68
    ),
    ClassicalQuote(
        quote = "千人之诺诺，不如一士之谔谔。",
        source = "《史记 · 商君列传》",
        searchKeyword = "千人之诺诺",
        chapterId = 68
    ),
    ClassicalQuote(
        quote = "智者千虑，必有一失；愚者千虑，必有一得。",
        source = "《史记 · 淮阴侯列传》",
        searchKeyword = "智者千虑",
        chapterId = 92
    ),
    ClassicalQuote(
        quote = "诸将易得耳。至如信者，国士无双。",
        source = "《史记 · 淮阴侯列传》",
        searchKeyword = "国士无双",
        chapterId = 92
    ),
    ClassicalQuote(
        quote = "臣多多而益善耳。",
        source = "《史记 · 淮阴侯列传》",
        searchKeyword = "多多而益善",
        chapterId = 92
    ),
    ClassicalQuote(
        quote = "乘人之车者载人之患，衣人之衣者怀人之忧，食人之食者死人之事。",
        source = "《史记 · 淮阴侯列传》",
        searchKeyword = "乘人之车者",
        chapterId = 92
    ),
    ClassicalQuote(
        quote = "此所谓“驱市人而战之”，其势非置之死地，使人人自为战。",
        source = "《史记 · 淮阴侯列传》",
        searchKeyword = "置之死地",
        chapterId = 92
    ),
    ClassicalQuote(
        quote = "得黄金百斤，不如得季布一诺。",
        source = "《史记 · 季布栾布列传》",
        searchKeyword = "季布一诺",
        chapterId = 100
    ),
    ClassicalQuote(
        quote = "人之所病，病疾多；而医之所病，病道少。",
        source = "《史记 · 扁鹊仓公列传》",
        searchKeyword = "病道少",
        chapterId = 105
    ),
    ClassicalQuote(
        quote = "桃李不言，下自成蹊。",
        source = "《史记 · 李将军列传》",
        searchKeyword = "桃李不言",
        chapterId = 109
    ),
    ClassicalQuote(
        quote = "其身正，不令而行；其身不正，虽令不从。",
        source = "《史记 · 李将军列传》",
        searchKeyword = "其身正",
        chapterId = 109
    ),
    ClassicalQuote(
        quote = "匈奴未灭，无以家为也。",
        source = "《史记 · 卫将军骠骑列传》",
        searchKeyword = "匈奴未灭",
        chapterId = 111
    ),
    ClassicalQuote(
        quote = "盖世必有非常之人，然后有非常之事；有非常之事，然后有非常之功。",
        source = "《史记 · 司马相如列传》",
        searchKeyword = "非常之人",
        chapterId = 117
    ),
    ClassicalQuote(
        quote = "一死一生，乃知交情。一贫一富，乃知交态。一贵一贱，交情乃见。",
        source = "《史记 · 汲郑列传》",
        searchKeyword = "一死一生",
        chapterId = 120
    ),
    ClassicalQuote(
        quote = "其言必信，其行必果，已诺必诚，不爱其躯，赴士之厄困。",
        source = "《史记 · 游侠列传》",
        searchKeyword = "其言必信",
        chapterId = 124
    ),
    ClassicalQuote(
        quote = "此鸟不飞则已，一飞冲天；不鸣则已，一鸣惊人。",
        source = "《史记 · 滑稽列传》",
        searchKeyword = "一飞冲天",
        chapterId = 126
    ),
    ClassicalQuote(
        quote = "天下熙熙，皆为利来；天下攘攘，皆为利往。",
        source = "《史记 · 货殖列传》",
        searchKeyword = "天下熙熙",
        chapterId = 129
    ),
    ClassicalQuote(
        quote = "人弃我取，人取我与。",
        source = "《史记 · 货殖列传》",
        searchKeyword = "人弃我取",
        chapterId = 129
    ),
    ClassicalQuote(
        quote = "法者，天子所与天下公共也。",
        source = "《史记 · 张释之冯唐列传》",
        searchKeyword = "天下公共",
        chapterId = 102
    ),
    ClassicalQuote(
        quote = "宁为鸡口，无为牛后。",
        source = "《史记 · 苏秦列传》",
        searchKeyword = "宁为鸡口",
        chapterId = 69
    ),
    ClassicalQuote(
        quote = "视吾舌尚在否？",
        source = "《史记 · 张仪列传》",
        searchKeyword = "视吾舌",
        chapterId = 70
    ),
    ClassicalQuote(
        quote = "究天人之际，通古今之变，成一家之言。",
        source = "《史记 · 太史公自序》",
        searchKeyword = "成一家之言",
        chapterId = 130
    ),
    ClassicalQuote(
        quote = "《诗》三百篇，大抵贤圣发愤之所为作也。",
        source = "《史记 · 太史公自序》",
        searchKeyword = "发愤",
        chapterId = 130
    )
)
