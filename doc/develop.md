# 开发

## 启动参数

启动分为两步

1. 第一步是launcher，用于拉取、升级镜像
2. 第二步是vpss本体的启动。

launcher支持如下启动参数：

1. `help|--help|-help|-h`，显示命令列表并退出
2. `version|--version|-version|-v`，打印版本号并退出
3. `--image-prefix=<>`，设置镜像前缀，如果指定了该参数，则在启动vpss时也会自动附加该参数

在`/etc/vpss/launch-vpss`中定义了vpss启动参数的可变项。由于软件本身的需求，VPSS启动器默认会做如下配置：

1. 使用`-d`运行于后台
2. 使用`--rm`确保退出后自动清理容器
3. 使用`net=host`
4. 挂载`/root`
5. 挂载`/etc/vpss`
6. 挂载`/var/run`
7. 附加完整权限`--privileged`
8. 设置容器名`vpss`

支持如下占位符：

1. `$image`，用于填充启动时使用的镜像名

VPSS支持如下启动参数：

1. `help|--help|-help|-h`，显示命令列表并退出
2. `version|--version|-version|-v`，打印版本号并退出
3. `--skip-dhcp=true|false`，用于跳过DHCP并使用空的默认网络配置，默认为false
4. `--no-session-timeout=true|false`，用于取消登录用户的session超时时间
5. `--include-network-interfaces=nic1,nic2,nic3`，用于包含额外的虚拟网口，默认情况下仅会处理物理网口。该选项可用于配合hostapd做热点
6. `--ignore-network-interfaces=nic1,nic2,nic3`，用于忽略网口，可用于忽略机器本身的管理网口
7. `--image-prefix=<>`，用于指定镜像前缀，在启动其他容器时使用。如果launcher接受了该参数，则其会自动为vpss传入该参数

## 页面文件

页面文件放置在`/vproxy/vpss/ui`路径下。如果想自定义UI，可以将html等文件挂载到这个路径中。

## 镜像tag

默认情况下vpss会使用`latest`作为镜像tag。可以通过配置`/etc/vpss/image-tags.json`文件更换默认镜像tag。  

该文件是一个json文件，key为如下可选项，value为其对应的tag：

* vpss-base
* vpss
* vpws-agent
* tools-dhclient
* tools-socat-with-dhclient
* vpws-agent-with-dhclient
* docker-plugin
* other

如果存在没有指定tag的镜像，则会使用`other`，如果`other`也没有指定，则使用默认值。

## 手动升级

VPSS支持版本升级。在开发时自动升级通常不可用。可以通过配置文件进行手动升级：

在`/etc/vpss/require-images`文件中，每一行为一项必须存在的镜像，空行或者以`#`开头的行会被跳过。如果镜像名不带`:`，则会按照`镜像tag`一节中描述的方式获取默认的镜像tag。如果镜像名以`/`开头，则会在其之前填充`vproxyio`。  
该文件中配置的镜像，会在vpss-launcher启动时检查其是否存在。如果不存在，则vpss-launcher会拉取该镜像。如果存在，则vpss-launcher不会做其他处理。

在`/etc/vpss/upgrade-images`文件中，每一行为一项待升级的镜像，空行或者以`#`开头的行会被跳过。如果镜像名不带`:`，则会按照`镜像tag`一节中描述的方式获取默认的镜像tag。如果镜像名以`/`开头，则会在其之前填充`vproxyio`。  
该文件中配置的镜像，会在vpss-launcher启动时尝试拉取新的镜像。如果拉取成功，则会删除老镜像，并使用新镜像启动系统。如果拉取失败，则依旧使用老镜像启动。

配置文件修改后，需要重启`vpss-launcher`:

```
docker restart vpss-launcher
```

## 检查配置

VPSS完全基于VProxy，所以也支持VProxy的`resp-controller`，允许用户通过`redis-cli`以检查或者修改配置。

登录VPSS节点，执行`redis-cli -s /var/run/vpss.sock -a vpss`即可使用命令操作VPSS。

建议仅通过该命令行工具查询状态，而不要修改配置。直接修改配置会造成VPSS和VProxy配置不一致，并且这里修改的配置不会被持久化。

## 编译和打包

所有动作均记录于Makefile中：

* `clean`: 清理工程
* `vproxy.jar`: 从github releases拉取vproxy jar包
* `vproxy-no-kt-runtime.jar`: 制作不附带kt运行时的vproxy jar包
* `pktgen`: 根据流表生成包处理基类（在更新流表后需要执行）
* `compile`: vpss编译打包
* `jar`: 同`compile`
* `compile-launcher`: 启动器编译打包，结果在`launcher/`工程的编译结果目录内
* `docker-base`: 制作vpss依赖的基础docker镜像
* `docker`: 制作vpss镜像
* `docker-launcher`: 制作启动器镜像
* `build`: 上述步骤的整合
* `docker-dhclient`: 制作附带dhclient的镜像
* `docker-iperf3-with-dhclient`: 制作附带iperf3的dhclient镜像
* `docker-socat-with-dhclient`: 制作附带socat的dhclient镜像
* `docker-vpws-agent-with-dhclient`: 制作`vpws-agent`镜像

此外，本工程还依赖vproxy docker network plugin，请在vproxy工程中编译打包。
