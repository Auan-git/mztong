package com.ahu.ahutong.data

/**
 * 民职通（民政职业大学版）集中配置。
 *
 * 重要说明：
 * - 目前只有学校主域名 [SCHOOL_DOMAIN] 是已知事实；教务处站点 jwc.bcsa.edu.cn、招生站点
 *   zs.bcsa.edu.cn 曾被访问过，但教务系统（jwxt）入口是否位于 jwc 之下尚未确认。
 * - 教务系统、统一身份认证（CAS）、一卡通/云卡、缴费平台、校园门户的真实地址尚未核实，
 *   下面的主机名都是「把原安徽大学域名机械替换为 bcsa.edu.cn」得到的占位值。
 *   部署、发版或对接学校网络前，必须逐项核对并替换为学校实际地址。
 * - 修改任何一个主机名时，必须同步检查本类的派生 URL、ApkUpdatePolicy 以及相关单元测试，
 *   避免 WebView 域名白名单、Cookie 域、请求 Referer/Origin 与实际请求不一致。
 * - 内部标识符（包名 com.ahu.ahutong、AhuTong、AdwmhApi 等）保持不变，避免影响
 *   已发布版本的缓存、持久化与类加载；它们不代表学校品牌。
 */
object SchoolConfig {

    // ---------------------------------------------------------------- 学校与应用标识

    const val SCHOOL_NAME = "民政职业大学"
    const val SCHOOL_SHORT = "民职大"
    const val APP_NAME = "民职通"

    /** 学校主域名（已知事实）。其下各子域名是否为真实服务地址仍需现场核对。 */
    const val SCHOOL_DOMAIN = "bcsa.edu.cn"

    // ---------------------------------------------------------------- 教务系统（超星，已确认真实地址）

    /**
     * 学校教务系统真实地址：超星智慧教务（2026-11 确认）。
     *
     * 课表抓取流程见 [com.ahu.ahutong.data.chaoxing.ChaoxingClient]，协议来自
     * `chaoxing-schedule` 抓取脚本的实测结果。
     */
    const val CHAOXING_BASE_URL = "https://bcsa.jw.chaoxing.com"

    /** 超星教务主机名，用于 WebView 域名白名单等。 */
    const val CHAOXING_HOST = "bcsa.jw.chaoxing.com"

    /** 登录页（GET，页面内含 RSA 公钥）。 */
    const val CHAOXING_LOGIN_PAGE_PATH = "/admin?sfjrxk=1"

    /** 登录提交（POST 表单）。 */
    const val CHAOXING_LOGIN_PATH = "/admin/login"

    /** 学生课表页（含 xhid / xnxq / xqdm 隐藏字段）。 */
    const val CHAOXING_STUDENT_TABLE_PATH = "/admin/pkgl/xskb/queryKbForXsd"

    /** 作息与校历接口。 */
    const val CHAOXING_TIME_CONFIG_PATH = "/admin/api/getZclistByXnxq"

    /** 排课明细接口。 */
    const val CHAOXING_LESSON_LIST_PATH = "/admin/pkgl/xskb/sdpkkbList"

    // ---------------------------------------------------------------- 旧教务系统（强智，已停用）
    //
    // 说明：学校教务已切换为超星，以下强智地址（jw.bcsa.edu.cn/*）在民政职业大学不可用，
    // 仅保留部分旧功能（成绩/考试/空教室/评教）的编译依赖与历史兼容，后续如无用可整体移除。

    // TODO(民政职业大学): 旧系统地址，已停用
    const val JWXT_HOST = "jw.$SCHOOL_DOMAIN"

    // TODO(民政职业大学): 待核对真实地址
    const val JWXT_BASE_URL = "https://$JWXT_HOST/"

    /** 与 JWXT_BASE_URL 同源、不带结尾斜杠，用于 Origin 头。 */
    // TODO(民政职业大学): 待核对真实地址
    const val JWXT_ORIGIN = "https://$JWXT_HOST"

    /** 教务 SSO 入口路径，WebView 登录与 CAS 回调都依赖它。 */
    // TODO(民政职业大学): 待核对真实地址
    const val JWXT_SSO_LOGIN_PATH = "/student/sso/login"

    // TODO(民政职业大学): 待核对真实地址
    const val JWXT_SSO_LOGIN_URL = "$JWXT_BASE_URL" + "student/sso/login"

    // TODO(民政职业大学): 待核对真实地址
    const val JWXT_HOME_PATH = "/student/home"

    // TODO(民政职业大学): 待核对真实地址
    const val JWXT_HOME_URL = "$JWXT_BASE_URL" + "student/home"

    /** 教务评价服务（eams5），EvaluationApi 的 Retrofit baseUrl。 */
    // TODO(民政职业大学): 待核对真实地址
    const val EVALUATION_SERVICE_URL = "$JWXT_BASE_URL" + "eams5-evaluation-service/"

    // TODO(民政职业大学): 待核对真实地址
    const val EVALUATION_REFERER = "$JWXT_BASE_URL" + "evaluation-student-frontend/?bizTypeId=2"

    // TODO(民政职业大学): 待核对真实地址
    const val STUDENT_HOME_REFERER = JWXT_HOME_URL

    // ---------------------------------------------------------------- 统一身份认证（CAS）

    // TODO(民政职业大学): 待核对真实地址
    const val CAS_HOST = "one.$SCHOOL_DOMAIN"

    // TODO(民政职业大学): 待核对真实地址
    const val CAS_BASE_URL = "https://$CAS_HOST/"

    // TODO(民政职业大学): 待核对真实地址
    const val CAS_DEVICE_URL = "$CAS_BASE_URL" + "cas/device"

    // TODO(民政职业大学): 待核对真实地址
    const val CAS_LOGIN_URL = "$CAS_BASE_URL" + "cas/login"

    // TODO(民政职业大学): 待核对真实地址
    const val CAS_LOGIN_PATH = "/cas/login"

    /**
     * 教务 SSO 回调地址的 URL 编码形式，用作 CAS login 的 service 参数。
     * 注意：它与 [JWXT_SSO_LOGIN_URL] 必须保持同源，改动 BASE_URL 时两者要一起改。
     */
    // TODO(民政职业大学): 待核对真实地址
    const val JWXT_SSO_SERVICE_ENCODED = "https%3A%2F%2Fjw.bcsa.edu.cn%2Fstudent%2Fsso%2Flogin"

    /** 完整的 CAS 登录 URL（带 service 参数）。 */
    // TODO(民政职业大学): 待核对真实地址
    const val CAS_JWXT_LOGIN_URL = "$CAS_LOGIN_URL" + "?service=$JWXT_SSO_SERVICE_ENCODED"

    // ---------------------------------------------------------------- 一卡通 / 云卡（Ycard）

    // TODO(民政职业大学): 待核对真实地址
    const val YCARD_HOST = "ycard.$SCHOOL_DOMAIN"

    // TODO(民政职业大学): 待核对真实地址
    const val YCARD_BASE_URL = "https://$YCARD_HOST/"

    /** 与 YCARD_BASE_URL 同源、不带结尾斜杠，用于 Origin 头。 */
    // TODO(民政职业大学): 待核对真实地址
    const val YCARD_ORIGIN = "https://$YCARD_HOST"

    /** 一卡通登录中转页 targetUrl。 */
    // TODO(民政职业大学): 待核对真实地址
    const val YCARD_LOGIN_TARGET_URL = "$YCARD_BASE_URL" + "plat/?name=loginTransit"

    /** 一卡通 H5 应用 Referer。 */
    // TODO(民政职业大学): 待核对真实地址
    const val YCARD_CHARGE_APP_REFERER = "$YCARD_BASE_URL" + "charge-app/"

    /** 支付完成后跳回的云卡首页。 */
    // TODO(民政职业大学): 待核对真实地址
    const val YCARD_PLAT_URL = "$YCARD_BASE_URL" + "plat"

    /** 银行卡充值支付完成后的 redirect_url。 */
    // TODO(民政职业大学): 待核对真实地址
    const val YCARD_PAYMENT_REDIRECT_URL = "$YCARD_BASE_URL" + "payment/?name=result"

    /** 网费/电费缴费入口 Referer。 */
    // TODO(民政职业大学): 待核对真实地址
    const val YCARD_NETWORK_ENTRY_REFERER = "$YCARD_BASE_URL" + "plat/dating?index=1"

    // ---------------------------------------------------------------- 校园门户（原 adwmh）

    /** 原 adwmh 智慧校园门户主机名；内部类名 AdwmhApi 保留不改。 */
    // TODO(民政职业大学): 待核对真实地址
    const val PORTAL_HOST = "adwmh.$SCHOOL_DOMAIN"

    // TODO(民政职业大学): 待核对真实地址
    const val PORTAL_BASE_URL = "https://$PORTAL_HOST/"

    // ---------------------------------------------------------------- 缴费平台（epay92）

    // TODO(民政职业大学): 待核对真实地址
    const val EPAY_HOST = "epay92.$SCHOOL_DOMAIN"

    // TODO(民政职业大学): 待核对真实地址
    const val EPAY_BASE_URL = "https://$EPAY_HOST/"

    // ---------------------------------------------------------------- 更新服务 / 遥测

    /** 更新、灰度、遥测、本地模型训练数据上传共用的服务域名（占位）。 */
    // TODO(民政职业大学): 待核对真实地址；学校需自行部署该服务。
    const val UPDATE_SERVER_HOST = "openmzt.org"

    // TODO(民政职业大学): 待核对真实地址
    const val UPDATE_SERVER_BASE_URL = "https://$UPDATE_SERVER_HOST"

    // TODO(民政职业大学): 待核对真实地址
    const val MIRROR_HOST = "mirror.$UPDATE_SERVER_HOST"

    // ---------------------------------------------------------------- Gitee 资源仓库（国内）

    /** Gitee API v5 根地址（公开仓库可不带 token 读取目录树）。 */
    const val GITEE_API_BASE_URL = "https://gitee.com/api/v5/"

    /** Gitee 网页地址，用于「在 Gitee 打开」与文件浏览链接。 */
    const val GITEE_WEB_BASE_URL = "https://gitee.com"

    /** 软件源码与学习资料所在的 Gitee 仓库 owner。 */
    const val RESOURCE_REPOSITORY_OWNER = "edge-of-the-abyss"

    /** 软件源码与学习资料所在的 Gitee 仓库名。 */
    const val RESOURCE_REPOSITORY_NAME = "ChinaCivil-Affairs-University-using"

    /** 资源仓库默认分支。 */
    const val RESOURCE_REPOSITORY_BRANCH = "master"

    /** 资源仓库中「学习资料」的根目录（各学院资料放在其下）。 */
    const val RESOURCE_MATERIALS_ROOT = "学习资料"

    /** 民职通开源仓库地址：意见反馈、贡献名单等入口统一链接到这里。 */
    const val PROJECT_REPOSITORY_URL =
        "$GITEE_WEB_BASE_URL/$RESOURCE_REPOSITORY_OWNER/$RESOURCE_REPOSITORY_NAME"

    /** 仓库 Issue 页面（反馈问题 / 提建议）。 */
    const val PROJECT_ISSUES_URL = "$PROJECT_REPOSITORY_URL/issues"

    // ---------------------------------------------------------------- 运行时开关

    /**
     * 是否使用预编译 Rust 服务（libahutong_rs.so）。
     *
     * 预编译的 libahutong_rs.so 内部仍绑定原安徽大学的服务地址，民政职业大学若不复用
     * 该服务，可置为 false 强制走 Android 爬虫（Rust 本地服务 → JNI → Android 爬虫 的
     * fallback 链不变）。
     *
     * 本次改造只新增常量与注释、不接线，避免影响现有编译与运行路径；接线时由
     * AHURepository/数据源选择处读取本开关即可。
     */
    const val USE_PRECOMPILED_RUST_SERVICE: Boolean = true
}