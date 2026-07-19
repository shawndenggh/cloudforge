# 飞书用户资源对象参考

> 调研日期：2026-07-19
> 资料范围：仅使用飞书/Lark 官方开放平台文档和官方 SDK。本文用于 CloudForge 领域建模参考，不是飞书模型的复制方案。

## 结论

飞书通讯录中的 `User` 表示企业组织架构中的成员实体，资源对象同时承载了账号身份、个人资料、雇佣关系、组织架构和企业授权信息。因此，它不能整体映射为 CloudForge 的全局 `User`。[用户资源介绍](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/contact-v3/user/field-overview)

CloudForge 应借鉴以下设计思想：

- 使用不可变的全局 `userId` 标识 User，登录邮箱可以修改但不能替代主键。
- 全局 User 只保存平台身份、最小个人资料和偏好；租户内身份及组织属性由 Employee 保存。
- 邮箱或手机号只有经过 CloudForge 自身验证后，才可作为登录凭证。飞书官方特别提示，管理员导入的联系方式未经过用户实时验证，不建议直接作为登录凭证。[获取用户信息](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/authen-v1/user_info/get)
- User 状态使用单一枚举，避免照搬飞书多个布尔状态后产生互相矛盾的组合。
- 头像资源可以提供不同尺寸，但 CloudForge MVP 无需把每个尺寸都设计成用户表字段。

## 飞书资源结构

飞书官方 Contact v3 `User` 模型包含以下字段组；完整字段也可在[官方 Java SDK User 模型](https://github.com/larksuite/oapi-sdk-java/blob/main/larksuite-oapi/src/main/java/com/lark/oapi/service/contact/v3/model/User.java)中交叉核对。

| 字段组 | 飞书字段 | 语义 | CloudForge 判断 |
|---|---|---|---|
| 多层身份标识 | `union_id`、`user_id`、`open_id` | 分别按开发者、租户、应用确定用户标识范围 | 不照搬；全局 User 使用单一 `userId`，第三方身份另表保存 |
| 名称 | `name`、`en_name`、`nickname` | 姓名、英文名、别名 | 全局 User 只需稳定的 `displayName`；租户正式姓名可由 Employee 管理 |
| 联系方式 | `email`、`mobile`、`mobile_visible`、`enterprise_email` | 普通邮箱、企业内唯一手机号、通讯录可见性、企业邮箱 | 已验证主邮箱可属于 User；企业联系方式和可见性属于 Employee |
| 头像 | `avatar_key`、`avatar` | 文件 Key，以及 72、240、640 和原图 URL | 借鉴按尺寸输出；MVP 只持久化一个头像资源引用或规范 URL |
| 状态 | `status` | 暂停、离职、激活、退出、未加入等多个布尔状态 | 只借鉴生命周期覆盖面，不复制状态结构 |
| 组织关系 | `department_ids`、`leader_user_id`、`positions`、`orders`、`department_path`、`dotted_line_leader_user_ids` | 部门、直属或虚线上级、岗位与通讯录排序 | 全部属于 organization-service 的 Employee |
| 雇佣资料 | `join_time`、`employee_no`、`employee_type`、`job_title`、`job_level_id`、`job_family_id` | 入职、工号、人员类型、职务、职级和序列 | 全部属于 Employee |
| 工作资料 | `city`、`country`、`work_station` | 工作城市、工作国家或地区、工位 | 属于 Employee，不是全局 User 偏好 |
| 企业能力 | `is_tenant_manager`、`subscription_ids`、`assign_info`、`custom_attrs`、`geo` | 企业管理员、席位、自定义通讯录字段或数据驻留信息 | 属于租户授权、企业配置或 Employee 扩展，不进入全局 User |
| 个人扩展 | `gender` | 性别 | 无明确需求时不收集，不因飞书存在该字段而照搬 |

飞书“创建用户”接口本身被描述为向通讯录创建用户、可理解为员工入职，并要求部门与人员类型等字段，这进一步说明该资源同时是企业员工模型。[创建用户](https://open.larksuite.com/document/server-docs/contact-v3/user/create)

## 状态设计参考

飞书 `UserStatus` 使用多个布尔值表达不同维度：

- `is_frozen`：账号暂停。
- `is_resigned`：员工离职。
- `is_activated`：账号已激活。
- `is_exited`：用户主动退出，随后可能转为离职。
- `is_unjoin`：尚未确认加入团队。

字段定义可见[官方 Java SDK UserStatus 模型](https://github.com/larksuite/oapi-sdk-java/blob/main/larksuite-oapi/src/main/java/com/lark/oapi/service/contact/v3/model/UserStatus.java)；飞书旧版状态变更文档也展示了激活、暂停和离职是不同状态维度。[用户状态变更](https://open.feishu.cn/document/ukTMukTMukTM/uITNxYjLyUTM24iM1EjN)

CloudForge 不应直接复制这些布尔字段。建议保持已确认的单一全局状态：

| CloudForge 状态 | 建议语义 | 与飞书概念的关系 |
|---|---|---|
| `INACTIVE` | User 已建立，但当前尚未启用任何平台访问能力 | 仅部分接近“未加入”，但不是租户邀请状态 |
| `PENDING_VERIFICATION` | 邮箱密码注册完成，主邮箱仍待验证 | CloudForge 自身的登录安全状态，飞书通讯录无直接等价项 |
| `ACTIVE` | 身份已验证，可建立 Session | 接近已激活且未冻结，但不代表有权访问任何租户 |
| `DISABLED` | 被平台禁用，全部 Session 立即失效 | 接近暂停，但属于平台级状态 |

`is_resigned`、`is_exited`、`is_unjoin` 描述的是某个企业内的 Employee 生命周期，不应映射成全局 User 状态。一个 User 从某租户离职后，仍可能是其他租户的有效 Employee。

## 邮箱与手机号

飞书用户对象同时暴露 `email`、`enterprise_email` 和 `mobile`，但这些字段受到通讯录字段权限控制。其登录用户信息接口明确警告：管理员导入的邮箱和手机号没有经过用户本人实时验证，不能直接当作登录凭证。[获取用户信息](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/authen-v1/user_info/get)

对 CloudForge 的建议：

- `primaryEmail` 放在全局 User，忽略大小写后全平台唯一；修改后必须重新验证。
- 明确记录邮箱验证结果，例如 `primaryEmailVerifiedAt`，不能仅凭 Employee 中存在同名邮箱就视为已验证。
- Employee 的工作邮箱只用于租户通讯录展示和待绑定匹配；成功绑定仍以已验证 User 邮箱为准。
- MVP 不把手机号放进全局 User。未来若支持手机号登录或账号恢复，应保存规范化号码及独立验证状态，不能直接复用 Employee 的工作手机号。
- `enterprise_email` 和 `mobile_visible` 明显依赖企业及通讯录策略，只属于 Employee。

## 头像、语言与时区

飞书头像对象提供 `avatar_72`、`avatar_240`、`avatar_640` 和 `avatar_origin`；字段定义可见[官方 Java SDK AvatarInfo 模型](https://github.com/larksuite/oapi-sdk-java/blob/main/larksuite-oapi/src/main/java/com/lark/oapi/service/contact/v3/model/AvatarInfo.java)。OAuth 用户信息接口也返回原图及多个尺寸的头像 URL。[获取用户信息](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/authen-v1/user_info/get)

CloudForge 可在统一 User 资源对象中提供 `avatarUrl`，由媒体层负责生成尺寸变体；不建议在 `users` 表中复制四个头像 URL 字段。

飞书当前公开的 Contact v3 [用户资源介绍](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/contact-v3/user/field-overview)、[获取单个用户信息](https://open.feishu.cn/document/server-docs/contact-v3/user/get?lang=zh-CN)和 Authen v1 [获取用户信息](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/authen-v1/user_info/get)均未把 `locale`、`language` 或 `time_zone` 列为公开用户字段。官方 Java SDK 的生成模型中虽然存在 `timeZone` 属性，但它没有出现在上述公开接口契约中，因此不应把它视为可依赖的飞书 API 字段。[官方 Java SDK User 模型](https://github.com/larksuite/oapi-sdk-java/blob/main/larksuite-oapi/src/main/java/com/lark/oapi/service/contact/v3/model/User.java)

飞书其他通讯录资源中的 `locale` 用于字段内容的国际化版本，例如人员类型名称，并不能证明它是用户语言偏好。[人员类型资源介绍](https://open.feishu.cn/document/server-docs/contact-v3/employee_type_enum/overview?lang=zh-CN)

因此：

- `timeZone` 适合作为 CloudForge 全局 User 偏好。
- `locale` 也适合作为 CloudForge 自身的产品语言偏好，但它是 CloudForge 的设计，不是从飞书 User 字段直接映射而来。
- 工作国家、工作城市与时区不能混为一谈；前两者属于 Employee，时区是用户级产品偏好。

## 企业身份字段的边界

飞书的 ID 设计区分应用级 `open_id`、开发者级 `union_id` 和租户级 `user_id`。官方批量查询接口说明了这三种 ID 的不同作用域。[批量获取用户信息](https://open.feishu.cn/document/contact-v3/user/batch)

CloudForge 应借鉴“身份标识有明确作用域”的原则，但不需要复制三套平台 ID：

- `users.id` 是 CloudForge 全局、永久且不可变的 User ID。
- GitHub、OIDC 等外部身份的提供方和主体标识放入独立身份绑定对象，不塞进 `users` 表。
- `tenantId`、Employee ID、租户角色和租户超级管理员标记均不得进入全局 User。
- 同一 User 在不同租户拥有不同 Employee；任何一个 Employee 的停用或离职都不改变 User 的平台级状态。

## 推荐的 CloudForge User 最小字段

Java 属性和 JSON 字段使用驼峰命名，PostgreSQL 列名使用下划线命名。以下是 CloudForge 的推荐模型，不是飞书字段翻译表。

| User 资源字段 | PostgreSQL 列 | 用途 | MVP 建议 |
|---|---|---|---|
| `id` | `id` | 永久、不可变的全局 User ID | 必需 |
| `primaryEmail` | `primary_email` | 当前主登录邮箱 | 必需 |
| `primaryEmailNormalized` | `primary_email_normalized` | 唯一性比较和登录查询使用的规范化邮箱 | 必需 |
| `primaryEmailVerifiedAt` | `primary_email_verified_at` | 主邮箱完成验证的时间 | 待验证时可空 |
| `displayName` | `display_name` | 平台级显示名称 | 必需 |
| `avatarUrl` | `avatar_url` | 平台级头像 URL | 可空 |
| `locale` | `locale` | 平台界面语言偏好 | 可空或使用系统默认值 |
| `timeZone` | `time_zone` | 用户时区偏好 | 可空或使用系统默认值 |
| `status` | `status` | `INACTIVE`、`PENDING_VERIFICATION`、`ACTIVE`、`DISABLED` | 必需 |
| `createdAt`、`updatedAt` | `created_at`、`updated_at` | 审计时间 | 必需 |

密码摘要、第三方身份、Session、邮箱验证令牌不属于标准 User 资源对象，应由独立的凭据、身份绑定和会话对象管理。Employee、部门、岗位和租户角色也不得作为 User 的嵌套默认字段；需要展示租户身份时，应显式按需加载对应 Employee 资源。
