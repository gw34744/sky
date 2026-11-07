# 配置安全（jasypt 加密）面试笔记

> 对应改动：`application.yml` / `application-dev.yml` 敏感信息加密、`sky-server/pom.xml` 引入 jasypt
> 主题：解决「配置文件明文敏感信息」这一安全硬伤

---

## 一、问题背景：为什么要做配置加密

黑马原版（以及大量教学项目）的 `application-dev.yml` 里，数据库密码、云服务密钥、微信支付密钥全部**明文**：

```yaml
sky:
  datasource:
    password: <你的数据库密码>          # 明文数据库密码
  alioss:
    access-key-id: LTAI5t******        # 明文阿里云 AccessKey
    access-key-secret: ******          # 明文阿里云 Secret
  wechat:
    apiV3Key: ******                   # 明文微信支付 APIv3 密钥
```

**风险**：
1. **代码仓库泄露 = 全部密钥泄露**：yml 随代码一起提交到 git，仓库一旦公开或泄露，数据库、云存储、支付密钥全部暴露；
2. **任何人能拿到仓库 = 能操作你的资源**：AccessKey 可登录阿里云、apiV3Key 可伪造支付请求；
3. **合规要求**：等保、公司安全规范都要求敏感信息不得明文入库。

---

## 二、jasypt 是什么、怎么工作

**jasypt**（Java Simplified Encryption）是 Java 配置加密的标准方案，`jasypt-spring-boot-starter` 让它和 Spring Boot 无缝集成。

**工作原理**：
- 配置里写密文：`password: ENC(加密后的密文)`
- 启动时，jasypt 用「解密密钥」把 `ENC(...)` 自动解密成明文，再注入 Spring 环境
- 解密密钥**不写在配置文件里**，而是通过环境变量 / JVM 参数 / 启动命令传入

```
配置文件(密文)  ──启动时──>  jasypt 解密器 ──密钥(环境变量)──> 明文注入 Spring
```

**核心设计原则**：**密钥与密文分离**。密文可以提交到仓库，密钥只能在运行时由运维/环境注入。这样即使仓库泄露，没有密钥也解不开。

---

## 三、落地实现

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.github.ulisesbocchio</groupId>
    <artifactId>jasypt-spring-boot-starter</artifactId>
    <version>3.0.5</version>
</dependency>
```

### 2. 配置加密算法（application.yml）

```yaml
jasypt:
  encryptor:
    algorithm: PBEWITHHMACSHA512ANDAES_256
    iv-generator-classname: org.jasypt.iv.RandomIvGenerator
```

### 3. 敏感信息替换为密文

```yaml
sky:
  datasource:
    password: ENC(UG/5d22c2uxK67UDZz35KC+...)
  wechat:
    apiV3Key: ENC(d1Uch3h6hOHKcYXTbOU9yFAnBkT...)
```

### 4. 密钥通过环境变量注入（关键！）

本地启动（IDEA VM options）：
```
-DJASYPT_ENCRYPTOR_PASSWORD=<你的加密密钥，不入库>
```

或系统环境变量：
```bash
export JASYPT_ENCRYPTOR_PASSWORD=<你的加密密钥，不入库>
```

生产环境由 CI/CD 或 K8s Secret 注入。

---

## 四、哪些字段要加密、哪些不用

| 类型 | 是否加密 | 例子 |
|------|---------|------|
| 数据库密码 | ✅ 必须 | `datasource.password` |
| 云服务 AccessKey | ✅ 必须 | `alioss.access-key-id/secret` |
| 微信支付密钥 | ✅ 必须 | `wechat.apiV3Key`、`mchSerialNo`、`secret` |
| JWT 签名密钥 | ✅ 应该 | `jwt.admin-secret-key`（泄露可伪造登录态） |
| 公钥标识/非敏感 | ❌ 不用 | `appid`、`mchid`（公开）、`endpoint`、`bucket-name` |
| 文件路径 | ❌ 不用 | `privateKeyFilePath`（是路径不是密钥） |
| 回调 URL | ❌ 不用 | `notifyUrl` |

**判断标准**：泄露后能否被用来「冒充身份 / 访问资源 / 伪造数据」。能 → 加密；不能 → 不加密。

---

## 五、高频面试问答

**Q1：为什么密钥不能也写进配置文件？**
A：如果密钥和密文放在同一个文件里，等于把「锁」和「钥匙」放在一起，泄露文件就等于泄露一切。密钥必须从运行时环境（环境变量、K8s Secret、配置中心）注入，与代码/配置分离。

**Q2：jasypt 的加密算法是什么？安全吗？**
A：默认 `PBEWITHHMACSHA512ANDAES_256`（PBE = Password Based Encryption，基于口令的加密），用 AES-256 加密 + HMAC-SHA512 做完整性校验，随机 IV。密钥越长越安全，生产建议用强随机密钥。

**Q3：密文是怎么生成的？**
A：用 jasypt 的 `StandardPBEStringEncryptor` 或命令行工具，指定算法 + 密钥，对明文加密得到 `ENC(...)` 字符串。加密是单向可逆（有密钥能解），但不知道密钥无法解密。

**Q4：jasypt 和 Spring Cloud Config / Vault 有什么区别？**
A：jasypt 是「配置值加密」，密钥仍需自己管理；Config/Vault 是「配置中心 + 密钥托管」，是更完整的方案。中小项目用 jasypt 够用，大型微服务用 Vault/KMS。

**Q5：改了密文后，老环境还能启动吗？**
A：不能。密文需要对应密钥才能解密。所有环境必须统一注入正确的 `JASYPT_ENCRYPTOR_PASSWORD`，否则启动报 `Unable to decrypt property`。

---

## 六、重要安全提醒（本次改动附带）

因为之前的明文密钥**已经进入过 git 提交历史**，单纯改密文还不够——历史里的明文仍然可见。所以：

1. **必须重置这些密钥**（让旧密钥失效）：
   - MySQL `root` 密码
   - 阿里云 AccessKey（在阿里云控制台删除旧的、新建一对）
   - 微信支付 APIv3 密钥（在微信商户平台重置）
2. 重置后重新生成密文，替换配置。

**面试可讲的点**：暴露「密钥进了 git 历史 → 光改文件不够 → 必须 rotate（轮换）密钥」这个安全意识，是加分项。

---

## 七、改动清单速记

| 文件 | 改动 |
|------|------|
| `sky-server/pom.xml` | 加 jasypt-spring-boot-starter 3.0.5 |
| `application.yml` | 加 jasypt 配置段 + JWT 密钥/baidu.ak 加密 |
| `application-dev.yml` | mysql/alioss/wechat 敏感信息全部 ENC 密文 |

启动需注入：`-DJASYPT_ENCRYPTOR_PASSWORD=<你的加密密钥，不入库>`
