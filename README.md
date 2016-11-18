# LXD client for Java

lxd-client is a Java REST API client for [LXD REST API](https://linuxcontainers.org/lxd/rest-api/). Leverages the following libraries to get the job done:

* OKHttp
* Jackson

## Credits

I wouldn't have been able to write this client without borrowing ideas and code from https://github.com/fabric8io/docker-client

Thanks to them!

## Developing with Vagrant

The root of this repos contains a Vagrantfile to start a VM with LXD. To launch it, you need Vagrant set up.

Then, just run:

    vagrant init
    vagrant ssh -- -L${PWD}/lxd.socket:/var/lib/lxd/unix.socket

Keep the SSH command open to access the LXD control socket from your computer. If it fails, remove the stale socket file:

    rm lxd.socket

## Debugging tips

### Accessing a remote LXD thought the Unix socket

Since OpenSSH 6.7, it's possible to forward [Unix sockets over SSH](https://lwn.net/Articles/609321/), so it's quite easy
to access LXD without having to bother with SSL certificates, networks and the likes...

    ssh  -L$HOME/tmp/lxd.socket:/var/lib/lxd/unix.socket ubuntu@server

### Using cURL with Unix socket

see https://www.stgraber.org/2016/04/18/lxd-api-direct-interaction/

    curl -s --unix-socket /var/lib/lxd/unix.socket a/1.0

### Capturing from the LXD Unix socket

There's no direct way to capture from a unix socket, like you could do on a network device. If you want to capture the
exchanges from `lxc` to `lxd` server, here's a trick: create a socat sandwitch! With `socat`, you can forward the
unix socket to a TCP port and then forward the TCP port to the original LXD socket. That way, you can snoop on localhost
network interface to capture the TCP traffic.

    # fake TCP server connects to real Unix socket
    socat TCP-LISTEN:6000,reuseaddr,fork,bind=localhost UNIX-CONNECT:/var/lib/lxd/unix.socket.orig
    # clients connect to this Unix socket
    sudo socat UNIX-LISTEN:/var/lib/lxd/unix.socket,mode=777,reuseaddr,fork TCP-CONNECT:127.0.0.1:6000

http://unix.stackexchange.com/questions/219853/how-to-passively-capture-from-unix-domain-sockets-af-unix-socket-monitoring

You can then capture the TCP stream and write them to a pcap file

    # start packet capture on said port
    tcpdump -i lo -w capture.pcap  port 6000 and host localhost
