# develop

## launching arguments

Launching is divided into two steps:

1. The launcher, which is used for pulling and upgrading images
2. The vpss it self

The launcher supports the following startup arguments:

1. `help|--help|-help|-h`, display command list and exit
2. `version|--version|-version|-v`, print versions and exit
3. `--image-prefix=<>`, is used to set the image prefix, if this param is set, then it will be passed to vpss.

Variables of launching arguments are defined in `/etc/vpss/launch-vpss`. Due to the need of the software itself, vpss launcher will set the following arguments:

1. use `-d` to ensure it's running as a daemon
2. use `--rm` to ensure the container is automatically destroyed after exiting
3. use `net=host`
4. mount `/root`
5. mount `/etc/vpss`
6. mount `/var/run`
7. enbling all privileges: `--privileged`
8. set container name `vpss`

The following placeholders are supported:

1. `$image`, will be filled with image name when launching

VPSS supports the following launching arguments:

1. `help|--help|-help|-h`, display command list and exit
2. `version|--version|-version|-v`, print version and exit
3. `--skip-dhcp=true|false`, is used to skip dhcp and use empty network configuration, default to false
4. `--no-session-timeout=true|false`, is used to cancel the session timeout for login users
5. `--include-network-interfaces=nic1,nic2,nic3`, is used to treat virtual nics as physical nics. Only physical nics will be managed by default. This argument can be used with hostapd to launch an AP.
6. `--ignore-network-interfaces=nic1,nic2,nic3`, is used to ignore interfaces. This argument can be used to ignore admin ports.
7. `--image-prefix=<>`, is used to set the image prefix, which is used when launching other containers. If launcher accepts this argument, then it will pass this argument to vpss.

## web page

Web page files are stored under `/vproxy/vpss/ui`. If you want to customize UI, you can mount files such as html to this path.

## image tag

VPSS will use `latest` as the image tag by default. You can change default tag by configuring `/etc/vpss/image-tags.json`.

This is a json file, key can be the following options and values correspond to its tag:

* vpss-base
* vpss
* vpws-agent
* tools-dhclient
* tools-socat-with-dhclient
* vpws-agent-with-dhclient
* docker-plugin
* other

If no specified tag, `other` will be used, if `other` is not set, then default value will be used.

## manually upgrade

VPSS supports upgrading. Automatic upgrading is usually not accessible during development. You may upgrade manually via the configuration files:

In file `/etc/vpss/require-images`, each line is an image that must exist. Empty lines or lines starting with `#` will be skipped. If the image doesn't contain `:`, then the default tag will be retrieved as described in chapter `image tag`. If the image starts with `/`, then it will be prefixed with `vproxyio`.  
Images configured in this file will be checked during the startup of vpss-launcher. If any of them does not exist, vpss-launcher will pull the image, otherwise vpss-launcher will not perform any operation.

In file `/etc/vpss/upgrade-images`, each line is an image that is to be upgraded. Empty lines or lines starting with `#` will be skipeed. If the image doesn't contain `:`, then the default tag will be retrieved as described in chapter `image tag`. If the image starts with `/`, then it will be prefixed with `vproxyio`.  
Images configured in this file will be pulled during the startup of vpss-launcher. If pulling is successful, the old image will be removed, and it will launch the system with the new image. If pulling failed, the old image will be used to launch the system.

After modifying the file, `vpss-launcher` should be restarted:

```
docker restart vpss-launcher
```

## inspect configuration

VPSS is fully based on VProxy, so it supports `resp-controller` to allow users to inspect or modify config via `redis-cli`.

Login into VPSS, execute `redis-cli -s /var/run/vpss.sock -a vpss`, then you can operate VPSS with vproxy commands.

It's recommended to only query status and not modifying configuration. Directly modification will cause VPSS config inconsistent with VProxy config, and all changes made in this way will not be persisted.

## Compile and Pack

All actions are recorded in Makefile:

* `clean`: clean the project
* `vproxy.jar`: pull vproxy.jar from github releases
* `vproxy-no-kt-runtime.jar`: make vproxy.jar without kt runtime
* `pktgen`: generate base class based on flow table (you should run this after updating flow table)
* `compile`: compile vpss
* `jar`: same as `compile`
* `compile-launcher`: compile launcher, the result will be located in the build folder in `launcher/` project
* `docker-base`: make the base docker image for vpss
* `docker`: make vpss image
* `docker-launcher`: make launcher image
* `build`: all above steps
* `docker-dhclient`: make image with dhclient
* `docker-iperf3-with-dhclient`: make iperf3 image with dhclient
* `docker-socat-with-dhclient`: make socat image with dhclient
* `docker-vpws-agent-with-dhclient`: make `vpws-agent` image

Also, this project depends on the vproxy docker network plugin, please compile and pack in the vproxy project.
