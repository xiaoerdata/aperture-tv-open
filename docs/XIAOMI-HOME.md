# 小米账号与米家设备

电视的小米账号由原系统管理；「工具 → 小米账号与家电 → 原系统账号设置」调用公开系统设置，若没有入口则指引回原桌面。它不提供开窗中的小米OAuth，也不读取电视账号、Cookie、token或设备列表。原生入口仍待小米实机确认。

设备显示使用你自己的Home Assistant。先安装 [小米官方 Xiaomi Home 集成](https://github.com/XiaoMi/ha_xiaomi_home)，在其授权流程登录并选择家庭/设备。其当前说明要求HA Core≥2024.4.4、OS≥13，设备类型、地区与本地模式有条件；蓝牙、红外和虚拟设备等以原项目支持范围为准。本地模式需要兼容的中枢网关，不承诺任意米家设备脱云控制。上游有独立许可，开窗不捆绑或复制其代码。

把 `examples/home-assistant.example.json` 复制到私有目录，用HA实体页的真实entity_id替换示例。在私有环境设置`APERTURE_HA_TOKEN`，用`APERTURE_HOME_ASSISTANT_CONFIG=/private/home-assistant.json npm start`加载。凭证只引用环境变量名，不进入源码或URL；家庭工作台不会回显它。使用HA普通专用用户的令牌；HA的令牌本身可能拥有较广权限，开窗的字段白名单并不改变HA原生令牌权限。为服务进程妥善保管令牌，可在HA撤销。

固定HA根地址，仅GET `/api/states/<已配置实体>`，不接受用户传入URL、entity_id、工具名或动作；不提供服务调用、开关或门锁控制。最多4组、每组6个字段；支持state与明确列举的温度、湿度、电量、亮度等属性。HTTP非回环地址需在私有配置显式`allowInsecureLan:true`；传输含Bearer凭证，实际家庭优先可信HTTPS。拒绝重定向，每次响应最多64KB，整体读取12秒上限。

家庭工作台 → 生活连接 → 读取预览。默认不勾选；只将你选择的字段分享到全家可见的快照中。预览绑定管理员会话、5分钟过期，权限与revision/幂等复用已有领域服务。电视标准版 → 数据，显示选中的状态；设备unknown/unavailable不显示旧属性数值。HA的last_updated不是测量时间，显示读取时间，不伪造成传感器采样时间。

当前是手动快照，无后台轮询设备/自动控制；过期显示待更新，移除快照可停止电视展示。撤销HA凭证后新读取失败，但已选择分享的快照仍保留至被移除或过期。爸妈版日常首页只保留三类观看，不混入设备管理。

实现：`server/life/home-assistant.mjs`接入既有life预览/分享服务；没有第二套共享数据库。验证：`node --test tests/home-assistant.test.mjs`、`node scripts/browser-home-assistant-qa.mjs`，使用合成HA响应与实际页面，不是厂商账号或家庭硬件验收。
