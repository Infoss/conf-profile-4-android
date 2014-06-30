/*
 * lwipopts.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef LWIPOPTS_H_
#define LWIPOPTS_H_

#include "lwip/debug.h"

/*
   -----------------------------------------------
   ---------- Platform specific locking ----------
   -----------------------------------------------
*/

/**
 * SYS_LIGHTWEIGHT_PROT==1: if you want inter-task protection for certain
 * critical regions during buffer allocation, deallocation and memory
 * allocation and deallocation.
 * Default: 0
 */
//define SYS_LIGHTWEIGHT_PROT            0

/**
 * NO_SYS==1: Provides VERY minimal functionality. Otherwise,
 * use lwIP facilities.
 * Default: 0
 */
//define NO_SYS                          0

/**
 * NO_SYS_NO_TIMERS==1: Drop support for sys_timeout when NO_SYS==1
 * Mainly for compatibility to old versions.
 * Default: 0
 */
//define NO_SYS_NO_TIMERS                0

/**
 * MEMCPY: override this if you have a faster implementation at hand than the
 * one included in your C library
 * Default: memcpy(dst,src,len)
 */
//define MEMCPY(dst,src,len)             memcpy(dst,src,len)

/**
 * SMEMCPY: override this with care! Some compilers (e.g. gcc) can inline a
 * call to memcpy() if the length is known at compile time and is small.
 * Default: memcpy(dst,src,len)
 */
//define SMEMCPY(dst,src,len)            memcpy(dst,src,len)

/*
   ------------------------------------
   ---------- Memory options ----------
   ------------------------------------
*/
/**
 * MEM_LIBC_MALLOC==1: Use malloc/free/realloc provided by your C-library
 * instead of the lwip internal allocator. Can save code size if you
 * already use it.
 * Default: 0
 */
//define MEM_LIBC_MALLOC                 0


/**
* MEMP_MEM_MALLOC==1: Use mem_malloc/mem_free instead of the lwip pool allocator.
* Especially useful with MEM_LIBC_MALLOC but handle with care regarding execution
* speed and usage from interrupts!
* Default: 0
*/
//define MEMP_MEM_MALLOC                 0

/**
 * MEM_ALIGNMENT: should be set to the alignment of the CPU
 *    4 byte alignment -> #define MEM_ALIGNMENT 4
 *    2 byte alignment -> #define MEM_ALIGNMENT 2
 * Default: 1
 */
#define MEM_ALIGNMENT                   4

/**
 * MEM_SIZE: the size of the heap memory. If the application will send
 * a lot of data that needs to be copied, this should be set high.
 * Default: 1600
 */
//define MEM_SIZE                        1600

/**
 * MEMP_SEPARATE_POOLS: if defined to 1, each pool is placed in its own array.
 * This can be used to individually change the location of each pool.
 * Default is one big array for all pools
 * Default: 0
 */
//define MEMP_SEPARATE_POOLS             0

/**
 * MEMP_OVERFLOW_CHECK: memp overflow protection reserves a configurable
 * amount of bytes before and after each memp element in every pool and fills
 * it with a prominent default value.
 *    MEMP_OVERFLOW_CHECK == 0 no checking
 *    MEMP_OVERFLOW_CHECK == 1 checks each element when it is freed
 *    MEMP_OVERFLOW_CHECK >= 2 checks each element in every pool every time
 *      memp_malloc() or memp_free() is called (useful but slow!)
 * Default: 0
 */
//define MEMP_OVERFLOW_CHECK             0

/**
 * MEMP_SANITY_CHECK==1: run a sanity check after each memp_free() to make
 * sure that there are no cycles in the linked lists.
 * Default: 0
 */
//define MEMP_SANITY_CHECK               0

/**
 * MEM_USE_POOLS==1: Use an alternative to malloc() by allocating from a set
 * of memory pools of various sizes. When mem_malloc is called, an element of
 * the smallest pool that can provide the length needed is returned.
 * To use this, MEMP_USE_CUSTOM_POOLS also has to be enabled.
 * Default: 0
 */
//define MEM_USE_POOLS                   0

/**
 * MEM_USE_POOLS_TRY_BIGGER_POOL==1: if one malloc-pool is empty, try the next
 * bigger pool - WARNING: THIS MIGHT WASTE MEMORY but it can make a system more
 * reliable.
 * Default: 0
 */
//define MEM_USE_POOLS_TRY_BIGGER_POOL   0

/**
 * MEMP_USE_CUSTOM_POOLS==1: whether to include a user file lwippools.h
 * that defines additional pools beyond the "standard" ones required
 * by lwIP. If you set this to 1, you must have lwippools.h in your
 * inlude path somewhere.
 * Default: 0
 */
//define MEMP_USE_CUSTOM_POOLS           0

/**
 * Set this to 1 if you want to free PBUF_RAM pbufs (or call mem_free()) from
 * interrupt context (or another context that doesn't allow waiting for a
 * semaphore).
 * If set to 1, mem_malloc will be protected by a semaphore and SYS_ARCH_PROTECT,
 * while mem_free will only use SYS_ARCH_PROTECT. mem_malloc SYS_ARCH_UNPROTECTs
 * with each loop so that mem_free can run.
 *
 * ATTENTION: As you can see from the above description, this leads to dis-/
 * enabling interrupts often, which can be slow! Also, on low memory, mem_malloc
 * can need longer.
 *
 * If you don't want that, at least for NO_SYS=0, you can still use the following
 * functions to enqueue a deallocation call which then runs in the tcpip_thread
 * context:
 * - pbuf_free_callback(p);
 * - mem_free_callback(m);
 * Default: 0
 */
//define LWIP_ALLOW_MEM_FREE_FROM_OTHER_CONTEXT 0

/*
   ------------------------------------------------
   ---------- Internal Memory Pool Sizes ----------
   ------------------------------------------------
*/
/**
 * MEMP_NUM_PBUF: the number of memp struct pbufs (used for PBUF_ROM and PBUF_REF).
 * If the application sends a lot of data out of ROM (or other static memory),
 * this should be set high.
 * Default: 16
 */
//define MEMP_NUM_PBUF                   16

/**
 * MEMP_NUM_RAW_PCB: Number of raw connection PCBs
 * (requires the LWIP_RAW option)
 * Default: 4
 */
#define MEMP_NUM_RAW_PCB                8

/**
 * MEMP_NUM_UDP_PCB: the number of UDP protocol control blocks. One
 * per active UDP "connection".
 * (requires the LWIP_UDP option)
 * Default: 4
 */
#define MEMP_NUM_UDP_PCB                256

/**
 * MEMP_NUM_TCP_PCB: the number of simulatenously active TCP connections.
 * (requires the LWIP_TCP option)
 * Default: 5
 */
#define MEMP_NUM_TCP_PCB                256

/**
 * MEMP_NUM_TCP_PCB_LISTEN: the number of listening TCP connections.
 * (requires the LWIP_TCP option)
 * Default: 8
 */
#define MEMP_NUM_TCP_PCB_LISTEN         32

/**
 * MEMP_NUM_TCP_SEG: the number of simultaneously queued TCP segments.
 * (requires the LWIP_TCP option)
 * Default: 16
 */
//define MEMP_NUM_TCP_SEG                16

/**
 * MEMP_NUM_REASSDATA: the number of IP packets simultaneously queued for
 * reassembly (whole packets, not fragments!)
 * Default: 5
 */
//define MEMP_NUM_REASSDATA              5

/**
 * MEMP_NUM_FRAG_PBUF: the number of IP fragments simultaneously sent
 * (fragments, not whole packets!).
 * This is only used with IP_FRAG_USES_STATIC_BUF==0 and
 * LWIP_NETIF_TX_SINGLE_PBUF==0 and only has to be > 1 with DMA-enabled MACs
 * where the packet is not yet sent when netif->output returns.
 * Default: 15
 */
//define MEMP_NUM_FRAG_PBUF              15

/**
 * MEMP_NUM_ARP_QUEUE: the number of simulateously queued outgoing
 * packets (pbufs) that are waiting for an ARP request (to resolve
 * their destination address) to finish.
 * (requires the ARP_QUEUEING option)
 * Default: 30
 */
#define MEMP_NUM_ARP_QUEUE              128

/**
 * MEMP_NUM_IGMP_GROUP: The number of multicast groups whose network interfaces
 * can be members et the same time (one per netif - allsystems group -, plus one
 * per netif membership).
 * (requires the LWIP_IGMP option)
 * Default: 8
 */
//define MEMP_NUM_IGMP_GROUP             8

/**
 * MEMP_NUM_SYS_TIMEOUT: the number of simulateously active timeouts.
 * (requires NO_SYS==0)
 * The default number of timeouts is calculated here for all enabled modules.
 * The formula expects settings to be either '0' or '1'.
 * Default: (LWIP_TCP + IP_REASSEMBLY + LWIP_ARP + (2*LWIP_DHCP) + LWIP_AUTOIP + LWIP_IGMP + LWIP_DNS + PPP_SUPPORT)
 */
//define MEMP_NUM_SYS_TIMEOUT            (LWIP_TCP + IP_REASSEMBLY + LWIP_ARP + (2*LWIP_DHCP) + LWIP_AUTOIP + LWIP_IGMP + LWIP_DNS + PPP_SUPPORT)

/**
 * MEMP_NUM_NETBUF: the number of struct netbufs.
 * (only needed if you use the sequential API, like api_lib.c)
 * Default: 2
 */
//define MEMP_NUM_NETBUF                 2

/**
 * MEMP_NUM_NETCONN: the number of struct netconns.
 * (only needed if you use the sequential API, like api_lib.c)
 * Default: 4
 */
//define MEMP_NUM_NETCONN                4

/**
 * MEMP_NUM_TCPIP_MSG_API: the number of struct tcpip_msg, which are used
 * for callback/timeout API communication.
 * (only needed if you use tcpip.c)
 * Default: 8
 */
//define MEMP_NUM_TCPIP_MSG_API          8

/**
 * MEMP_NUM_TCPIP_MSG_INPKT: the number of struct tcpip_msg, which are used
 * for incoming packets.
 * (only needed if you use tcpip.c)
 * Default: 8
 */
//define MEMP_NUM_TCPIP_MSG_INPKT        8

/**
 * MEMP_NUM_SNMP_NODE: the number of leafs in the SNMP tree.
 * Default: 50
 */
//define MEMP_NUM_SNMP_NODE              50

/**
 * MEMP_NUM_SNMP_ROOTNODE: the number of branches in the SNMP tree.
 * Every branch has one leaf (MEMP_NUM_SNMP_NODE) at least!
 * Default: 30
 */
//define MEMP_NUM_SNMP_ROOTNODE          30

/**
 * MEMP_NUM_SNMP_VARBIND: the number of concurrent requests (does not have to
 * be changed normally) - 2 of these are used per request (1 for input,
 * 1 for output)
 * Default: 2
 */
//define MEMP_NUM_SNMP_VARBIND           2

/**
 * MEMP_NUM_SNMP_VALUE: the number of OID or values concurrently used
 * (does not have to be changed normally) - 3 of these are used per request
 * (1 for the value read and 2 for OIDs - input and output)
 * Default: 3
 */
//define MEMP_NUM_SNMP_VALUE             3

/**
 * MEMP_NUM_NETDB: the number of concurrently running lwip_addrinfo() calls
 * (before freeing the corresponding memory using lwip_freeaddrinfo()).
 * Default: 1
 */
//define MEMP_NUM_NETDB                  1

/**
 * MEMP_NUM_LOCALHOSTLIST: the number of host entries in the local host list
 * if DNS_LOCAL_HOSTLIST_IS_DYNAMIC==1.
 * Default: 1
 */
//define MEMP_NUM_LOCALHOSTLIST          1

/**
 * MEMP_NUM_PPPOE_INTERFACES: the number of concurrently active PPPoE
 * interfaces (only used with PPPOE_SUPPORT==1)
 * Default: 1
 */
//define MEMP_NUM_PPPOE_INTERFACES       1

/**
 * PBUF_POOL_SIZE: the number of buffers in the pbuf pool.
 * Default: 16
 */
//define PBUF_POOL_SIZE                  16

/*
   ---------------------------------
   ---------- ARP options ----------
   ---------------------------------
*/
/**
 * LWIP_ARP==1: Enable ARP functionality.
 * Default: 1
 */
//define LWIP_ARP                        1

/**
 * ARP_TABLE_SIZE: Number of active MAC-IP address pairs cached.
 * Default: 10
 */
#define ARP_TABLE_SIZE                  127

/**
 * ARP_QUEUEING==1: Multiple outgoing packets are queued during hardware address
 * resolution. By default, only the most recent packet is queued per IP address.
 * This is sufficient for most protocols and mainly reduces TCP connection
 * startup time. Set this to 1 if you know your application sends more than one
 * packet in a row to an IP address that is not in the ARP cache.
 * Default: 0
 */
//define ARP_QUEUEING                    0

/**
 * ETHARP_TRUST_IP_MAC==1: Incoming IP packets cause the ARP table to be
 * updated with the source MAC and IP addresses supplied in the packet.
 * You may want to disable this if you do not trust LAN peers to have the
 * correct addresses, or as a limited approach to attempt to handle
 * spoofing. If disabled, lwIP will need to make a new ARP request if
 * the peer is not already in the ARP table, adding a little latency.
 * The peer *is* in the ARP table if it requested our address before.
 * Also notice that this slows down input processing of every IP packet!
 * Default: 0
 */
//define ETHARP_TRUST_IP_MAC             0

/**
 * ETHARP_SUPPORT_VLAN==1: support receiving ethernet packets with VLAN header.
 * Additionally, you can define ETHARP_VLAN_CHECK to an u16_t VLAN ID to check.
 * If ETHARP_VLAN_CHECK is defined, only VLAN-traffic for this VLAN is accepted.
 * If ETHARP_VLAN_CHECK is not defined, all traffic is accepted.
 * Alternatively, define a function/define ETHARP_VLAN_CHECK_FN(eth_hdr, vlan)
 * that returns 1 to accept a packet or 0 to drop a packet.
 * Default: 0
 */
//define ETHARP_SUPPORT_VLAN             0

/** LWIP_ETHERNET==1: enable ethernet support for PPPoE even though ARP
 * might be disabled
 * Default: (LWIP_ARP || PPPOE_SUPPORT)
 */
//define LWIP_ETHERNET                   (LWIP_ARP || PPPOE_SUPPORT)

/** ETH_PAD_SIZE: number of bytes added before the ethernet header to ensure
 * alignment of payload after that header. Since the header is 14 bytes long,
 * without this padding e.g. addresses in the IP header will not be aligned
 * on a 32-bit boundary, so setting this to 2 can speed up 32-bit-platforms.
 * Default: 0
 */
#define ETH_PAD_SIZE                    2

/** ETHARP_SUPPORT_STATIC_ENTRIES==1: enable code to support static ARP table
 * entries (using etharp_add_static_entry/etharp_remove_static_entry).
 * Default: 0
 */
//define ETHARP_SUPPORT_STATIC_ENTRIES   0


/*
   --------------------------------
   ---------- IP options ----------
   --------------------------------
*/
/**
 * IP_FORWARD==1: Enables the ability to forward IP packets across network
 * interfaces. If you are going to run lwIP on a device with only one network
 * interface, define this to 0.
 * Default: 0
 */
//define IP_FORWARD                      0

/**
 * IP_OPTIONS_ALLOWED: Defines the behavior for IP options.
 *      IP_OPTIONS_ALLOWED==0: All packets with IP options are dropped.
 *      IP_OPTIONS_ALLOWED==1: IP options are allowed (but not parsed).
 * Default: 1
 */
//define IP_OPTIONS_ALLOWED              1

/**
 * IP_REASSEMBLY==1: Reassemble incoming fragmented IP packets. Note that
 * this option does not affect outgoing packet sizes, which can be controlled
 * via IP_FRAG.
 * Default: 1
 */
//define IP_REASSEMBLY                   1

/**
 * IP_FRAG==1: Fragment outgoing IP packets if their size exceeds MTU. Note
 * that this option does not affect incoming packet sizes, which can be
 * controlled via IP_REASSEMBLY.
 * Default: 1
 */
//define IP_FRAG                         1

/**
 * IP_REASS_MAXAGE: Maximum time (in multiples of IP_TMR_INTERVAL - so seconds, normally)
 * a fragmented IP packet waits for all fragments to arrive. If not all fragments arrived
 * in this time, the whole packet is discarded.
 * Default: 3
 */
//define IP_REASS_MAXAGE                 3

/**
 * IP_REASS_MAX_PBUFS: Total maximum amount of pbufs waiting to be reassembled.
 * Since the received pbufs are enqueued, be sure to configure
 * PBUF_POOL_SIZE > IP_REASS_MAX_PBUFS so that the stack is still able to receive
 * packets even if the maximum amount of fragments is enqueued for reassembly!
 * Default: 10
 */
//define IP_REASS_MAX_PBUFS              10

/**
 * IP_FRAG_USES_STATIC_BUF==1: Use a static MTU-sized buffer for IP
 * fragmentation. Otherwise pbufs are allocated and reference the original
 * packet data to be fragmented (or with LWIP_NETIF_TX_SINGLE_PBUF==1,
 * new PBUF_RAM pbufs are used for fragments).
 * ATTENTION: IP_FRAG_USES_STATIC_BUF==1 may not be used for DMA-enabled MACs!
 * Default: 0
 */
//define IP_FRAG_USES_STATIC_BUF         0

/**
 * IP_FRAG_MAX_MTU: Assumed max MTU on any interface for IP frag buffer
 * (requires IP_FRAG_USES_STATIC_BUF==1)
 * Default: 1500
 */
//define IP_FRAG_MAX_MTU                 1500

/**
 * IP_DEFAULT_TTL: Default value for Time-To-Live used by transport layers.
 * Default: 255
 */
//define IP_DEFAULT_TTL                  255

/**
 * IP_SOF_BROADCAST=1: Use the SOF_BROADCAST field to enable broadcast
 * filter per pcb on udp and raw send operations. To enable broadcast filter
 * on recv operations, you also have to set IP_SOF_BROADCAST_RECV=1.
 * Default: 0
 */
//define IP_SOF_BROADCAST                0

/**
 * IP_SOF_BROADCAST_RECV (requires IP_SOF_BROADCAST=1) enable the broadcast
 * filter on recv operations.
 * Default: 0
 */
//define IP_SOF_BROADCAST_RECV           0

/**
 * IP_FORWARD_ALLOW_TX_ON_RX_NETIF==1: allow ip_forward() to send packets back
 * out on the netif where it was received. This should only be used for
 * wireless networks.
 * ATTENTION: When this is 1, make sure your netif driver correctly marks incoming
 * link-layer-broadcast/multicast packets as such using the corresponding pbuf flags!
 * Default: 0
 */
//define IP_FORWARD_ALLOW_TX_ON_RX_NETIF 0

/**
 * LWIP_RANDOMIZE_INITIAL_LOCAL_PORTS==1: randomize the local port for the first
 * local TCP/UDP pcb (default==0). This can prevent creating predictable port
 * numbers after booting a device.
 * Default: 0
 */
//define LWIP_RANDOMIZE_INITIAL_LOCAL_PORTS 0

/*
   ----------------------------------
   ---------- ICMP options ----------
   ----------------------------------
*/
/**
 * LWIP_ICMP==1: Enable ICMP module inside the IP stack.
 * Be careful, disable that make your product non-compliant to RFC1122
 * Default: 1
 */
//define LWIP_ICMP                       1

/**
 * ICMP_TTL: Default value for Time-To-Live used by ICMP packets.
 * Default: (IP_DEFAULT_TTL)
 */
//define ICMP_TTL                       (IP_DEFAULT_TTL)

/**
 * LWIP_BROADCAST_PING==1: respond to broadcast pings (default is unicast only)
 * Default: 0
 */
//define LWIP_BROADCAST_PING             0

/**
 * LWIP_MULTICAST_PING==1: respond to multicast pings (default is unicast only)
 * Default: 0
 */
//define LWIP_MULTICAST_PING             0

/*
   ---------------------------------
   ---------- RAW options ----------
   ---------------------------------
*/
/**
 * LWIP_RAW==1: Enable application layer to hook into the IP layer itself.
 * Default: 1
 */
//define LWIP_RAW                        1

/**
 * LWIP_RAW==1: Enable application layer to hook into the IP layer itself.
 * Default: (IP_DEFAULT_TTL)
 */
//define RAW_TTL                        (IP_DEFAULT_TTL)

/*
   ----------------------------------
   ---------- DHCP options ----------
   ----------------------------------
*/
/**
 * LWIP_DHCP==1: Enable DHCP module.
 * Default: 0
 */
//define LWIP_DHCP                       0

/**
 * DHCP_DOES_ARP_CHECK==1: Do an ARP check on the offered address.
 * Default: ((LWIP_DHCP) && (LWIP_ARP))
 */
//define DHCP_DOES_ARP_CHECK             ((LWIP_DHCP) && (LWIP_ARP))

/*
   ------------------------------------
   ---------- AUTOIP options ----------
   ------------------------------------
*/
/**
 * LWIP_AUTOIP==1: Enable AUTOIP module.
 * Default: 0
 */
//define LWIP_AUTOIP                     0

/**
 * LWIP_DHCP_AUTOIP_COOP==1: Allow DHCP and AUTOIP to be both enabled on
 * the same interface at the same time.
 * Default: 0
 */
//define LWIP_DHCP_AUTOIP_COOP           0

/**
 * LWIP_DHCP_AUTOIP_COOP_TRIES: Set to the number of DHCP DISCOVER probes
 * that should be sent before falling back on AUTOIP. This can be set
 * as low as 1 to get an AutoIP address very quickly, but you should
 * be prepared to handle a changing IP address when DHCP overrides
 * AutoIP.
 * Default: 9
 */
//define LWIP_DHCP_AUTOIP_COOP_TRIES     9

/*
   ----------------------------------
   ---------- SNMP options ----------
   ----------------------------------
*/
/**
 * LWIP_SNMP==1: Turn on SNMP module. UDP must be available for SNMP
 * transport.
 * Default: 0
 */
//define LWIP_SNMP                       0

/**
 * SNMP_CONCURRENT_REQUESTS: Number of concurrent requests the module will
 * allow. At least one request buffer is required.
 * Does not have to be changed unless external MIBs answer request asynchronously
 * Default: 1
 */
//define SNMP_CONCURRENT_REQUESTS        1

/**
 * SNMP_TRAP_DESTINATIONS: Number of trap destinations. At least one trap
 * destination is required
 * Default: 1
 */
//define SNMP_TRAP_DESTINATIONS          1

/**
 * SNMP_PRIVATE_MIB:
 * When using a private MIB, you have to create a file 'private_mib.h' that contains
 * a 'struct mib_array_node mib_private' which contains your MIB.
 * Default: 0
 */
//define SNMP_PRIVATE_MIB                0

/**
 * Only allow SNMP write actions that are 'safe' (e.g. disabeling netifs is not
 * a safe action and disabled when SNMP_SAFE_REQUESTS = 1).
 * Unsafe requests are disabled by default!
 * Default: 1
 */
//define SNMP_SAFE_REQUESTS              1

/**
 * The maximum length of strings used. This affects the size of
 * MEMP_SNMP_VALUE elements.
 * Default: 127
 */
//define SNMP_MAX_OCTET_STRING_LEN       127

/**
 * The maximum depth of the SNMP tree.
 * With private MIBs enabled, this depends on your MIB!
 * This affects the size of MEMP_SNMP_VALUE elements.
 * Default: 15
 */
//define SNMP_MAX_TREE_DEPTH             15

/**
 * The size of the MEMP_SNMP_VALUE elements, normally calculated from
 * SNMP_MAX_OCTET_STRING_LEN and SNMP_MAX_TREE_DEPTH.
 * Default: LWIP_MAX((SNMP_MAX_OCTET_STRING_LEN)+1, sizeof(s32_t)*(SNMP_MAX_TREE_DEPTH))
 */
//define SNMP_MAX_VALUE_SIZE             LWIP_MAX((SNMP_MAX_OCTET_STRING_LEN)+1, sizeof(s32_t)*(SNMP_MAX_TREE_DEPTH))

/*
   ----------------------------------
   ---------- IGMP options ----------
   ----------------------------------
*/
/**
 * LWIP_IGMP==1: Turn on IGMP module.
 * Default: 0
 */
//define LWIP_IGMP                       0

/*
   ----------------------------------
   ---------- DNS options -----------
   ----------------------------------
*/
/**
 * LWIP_DNS==1: Turn on DNS module. UDP must be available for DNS
 * transport.
 * Default: 0
 */
//define LWIP_DNS                        0

/**
 * DNS maximum number of entries to maintain locally.
 * Default: 4
 */
//define DNS_TABLE_SIZE                  4

/**
 * DNS maximum host name length supported in the name table.
 * Default: 256
 */
//define DNS_MAX_NAME_LENGTH             256

/**
 * The maximum of DNS servers
 * Default: 2
 */
//define DNS_MAX_SERVERS                 2

/**
 * DNS do a name checking between the query and the response.
 * Default: 1
 */
//define DNS_DOES_NAME_CHECK             1

/**
 * DNS message max. size. Default value is RFC compliant.
 * Default: 512
 */
//define DNS_MSG_SIZE                    512

/** DNS_LOCAL_HOSTLIST: Implements a local host-to-address list. If enabled,
 *  you have to define
 *    #define DNS_LOCAL_HOSTLIST_INIT {{"host1", 0x123}, {"host2", 0x234}}
 *  (an array of structs name/address, where address is an u32_t in network
 *  byte order).
 *
 *  Instead, you can also use an external function:
 *  #define DNS_LOOKUP_LOCAL_EXTERN(x) extern u32_t my_lookup_function(const char *name)
 *  that returns the IP address or INADDR_NONE if not found.
 *
 *  Default: 0
 */
//define DNS_LOCAL_HOSTLIST              0

/**
 * If this is turned on, the local host-list can be dynamically changed
 * at runtime.
 * Default: 0
 */
//define DNS_LOCAL_HOSTLIST_IS_DYNAMIC   0

/*
   ---------------------------------
   ---------- UDP options ----------
   ---------------------------------
*/
/**
 * LWIP_UDP==1: Turn on UDP.
 * Default: 1
 */
//define LWIP_UDP                        1

/**
 * LWIP_UDPLITE==1: Turn on UDP-Lite. (Requires LWIP_UDP)
 * Default: 0
 */
//define LWIP_UDPLITE                    0

/**
 * UDP_TTL: Default Time-To-Live value.
 * Default: (IP_DEFAULT_TTL)
 */
//define UDP_TTL                         (IP_DEFAULT_TTL)

/**
 * LWIP_NETBUF_RECVINFO==1: append destination addr and port to every netbuf.
 * Default: 0
 */
//define LWIP_NETBUF_RECVINFO            0

/*
   ---------------------------------
   ---------- TCP options ----------
   ---------------------------------
*/
/**
 * LWIP_TCP==1: Turn on TCP.
 * Default: 1
 */
//define LWIP_TCP                        1

/**
 * TCP_TTL: Default Time-To-Live value.
 * Default: (IP_DEFAULT_TTL)
 */
//define TCP_TTL                         (IP_DEFAULT_TTL)

/**
 * TCP_WND: The size of a TCP window.  This must be at least
 * (2 * TCP_MSS) for things to work well
 * Default: (4 * TCP_MSS)
 */
//define TCP_WND                         (4 * TCP_MSS)

/**
 * TCP_MAXRTX: Maximum number of retransmissions of data segments.
 * Default: 12
 */
//define TCP_MAXRTX                      12

/**
 * TCP_SYNMAXRTX: Maximum number of retransmissions of SYN segments.
 * Default: 6
 */
//define TCP_SYNMAXRTX                   6

/**
 * TCP_QUEUE_OOSEQ==1: TCP will queue segments that arrive out of order.
 * Define to 0 if your device is low on memory.
 * Default: (LWIP_TCP)
 */
//define TCP_QUEUE_OOSEQ                 (LWIP_TCP)

/**
 * TCP_MSS: TCP Maximum segment size. (default is 536, a conservative default,
 * you might want to increase this.)
 * For the receive side, this MSS is advertised to the remote side
 * when opening a connection. For the transmit size, this MSS sets
 * an upper limit on the MSS advertised by the remote host.
 * Default: 536
 */
//define TCP_MSS                         536

/**
 * TCP_CALCULATE_EFF_SEND_MSS: "The maximum size of a segment that TCP really
 * sends, the 'effective send MSS,' MUST be the smaller of the send MSS (which
 * reflects the available reassembly buffer size at the remote host) and the
 * largest size permitted by the IP layer" (RFC 1122)
 * Setting this to 1 enables code that checks TCP_MSS against the MTU of the
 * netif used for a connection and limits the MSS if it would be too big otherwise.
 * Default: 1
 */
//define TCP_CALCULATE_EFF_SEND_MSS      1


/**
 * TCP_SND_BUF: TCP sender buffer space (bytes).
 * To achieve good performance, this should be at least 2 * TCP_MSS.
 * Default: (2 * TCP_MSS)
 */
//define TCP_SND_BUF                     (2 * TCP_MSS)

/**
 * TCP_SND_QUEUELEN: TCP sender buffer space (pbufs). This must be at least
 * as much as (2 * TCP_SND_BUF/TCP_MSS) for things to work.
 * Default: ((4 * (TCP_SND_BUF) + (TCP_MSS - 1))/(TCP_MSS))
 */
//define TCP_SND_QUEUELEN                ((4 * (TCP_SND_BUF) + (TCP_MSS - 1))/(TCP_MSS))

/**
 * TCP_SNDLOWAT: TCP writable space (bytes). This must be less than
 * TCP_SND_BUF. It is the amount of space which must be available in the
 * TCP snd_buf for select to return writable (combined with TCP_SNDQUEUELOWAT).
 * Default: LWIP_MIN(LWIP_MAX(((TCP_SND_BUF)/2), (2 * TCP_MSS) + 1), (TCP_SND_BUF) - 1)
 */
//define TCP_SNDLOWAT                    LWIP_MIN(LWIP_MAX(((TCP_SND_BUF)/2), (2 * TCP_MSS) + 1), (TCP_SND_BUF) - 1)

/**
 * TCP_SNDQUEUELOWAT: TCP writable bufs (pbuf count). This must be less
 * than TCP_SND_QUEUELEN. If the number of pbufs queued on a pcb drops below
 * this number, select returns writable (combined with TCP_SNDLOWAT).
 * Default: LWIP_MAX(((TCP_SND_QUEUELEN)/2), 5)
 */
//define TCP_SNDQUEUELOWAT               LWIP_MAX(((TCP_SND_QUEUELEN)/2), 5)

/**
 * TCP_OOSEQ_MAX_BYTES: The maximum number of bytes queued on ooseq per pcb.
 * Default is 0 (no limit). Only valid for TCP_QUEUE_OOSEQ==0.
 * Default: 0
 */
//define TCP_OOSEQ_MAX_BYTES             0

/**
 * TCP_OOSEQ_MAX_PBUFS: The maximum number of pbufs queued on ooseq per pcb.
 * Default is 0 (no limit). Only valid for TCP_QUEUE_OOSEQ==0.
 * Default: 0
 */
//define TCP_OOSEQ_MAX_PBUFS             0

/**
 * TCP_LISTEN_BACKLOG: Enable the backlog option for tcp listen pcb.
 * Default: 0
 */
//define TCP_LISTEN_BACKLOG              0

/**
 * The maximum allowed backlog for TCP listen netconns.
 * This backlog is used unless another is explicitly specified.
 * 0xff is the maximum (u8_t).
 * Default: 0xff
 */
//define TCP_DEFAULT_LISTEN_BACKLOG      0xff

/**
 * TCP_OVERSIZE: The maximum number of bytes that tcp_write may
 * allocate ahead of time in an attempt to create shorter pbuf chains
 * for transmission. The meaningful range is 0 to TCP_MSS. Some
 * suggested values are:
 *
 * 0:         Disable oversized allocation. Each tcp_write() allocates a new
              pbuf (old behaviour).
 * 1:         Allocate size-aligned pbufs with minimal excess. Use this if your
 *            scatter-gather DMA requires aligned fragments.
 * 128:       Limit the pbuf/memory overhead to 20%.
 * TCP_MSS:   Try to create unfragmented TCP packets.
 * TCP_MSS/4: Try to create 4 fragments or less per TCP packet.
 *
 * Default: TCP_MSS
 */
//define TCP_OVERSIZE                    TCP_MSS

/**
 * LWIP_TCP_TIMESTAMPS==1: support the TCP timestamp option.
 * Default: 0
 */
//define LWIP_TCP_TIMESTAMPS             0

/**
 * TCP_WND_UPDATE_THRESHOLD: difference in window to trigger an
 * explicit window update
 * Default: (TCP_WND / 4)
 */
//define TCP_WND_UPDATE_THRESHOLD   (TCP_WND / 4)

/**
 * LWIP_EVENT_API and LWIP_CALLBACK_API: Only one of these should be set to 1.
 *     LWIP_EVENT_API==1: The user defines lwip_tcp_event() to receive all
 *         events (accept, sent, etc) that happen in the system.
 *     LWIP_CALLBACK_API==1: The PCB callback function is called directly
 *         for the event. This is the default.
 *
 * Default: LWIP_CALLBACK_API == 1
 */
//define LWIP_EVENT_API                  0
//define LWIP_CALLBACK_API               1


/*
   ----------------------------------
   ---------- Pbuf options ----------
   ----------------------------------
*/
/**
 * PBUF_LINK_HLEN: the number of bytes that should be allocated for a
 * link level header. The default is 14, the standard value for
 * Ethernet.
 * Default: (14 + ETH_PAD_SIZE)
 */
//define PBUF_LINK_HLEN                  (14 + ETH_PAD_SIZE)

/**
 * PBUF_POOL_BUFSIZE: the size of each pbuf in the pbuf pool. The default is
 * designed to accomodate single full size TCP frame in one pbuf, including
 * TCP_MSS, IP header, and link header.
 * Default: LWIP_MEM_ALIGN_SIZE(TCP_MSS+40+PBUF_LINK_HLEN)
 */
//define PBUF_POOL_BUFSIZE               LWIP_MEM_ALIGN_SIZE(TCP_MSS+40+PBUF_LINK_HLEN)

/*
   ------------------------------------------------
   ---------- Network Interfaces options ----------
   ------------------------------------------------
*/
/**
 * LWIP_NETIF_HOSTNAME==1: use DHCP_OPTION_HOSTNAME with netif's hostname
 * field.
 * Default: 0
 */
//define LWIP_NETIF_HOSTNAME             0

/**
 * LWIP_NETIF_API==1: Support netif api (in netifapi.c)
 * Default: 0
 */
//define LWIP_NETIF_API                  0

/**
 * LWIP_NETIF_STATUS_CALLBACK==1: Support a callback function whenever an interface
 * changes its up/down status (i.e., due to DHCP IP acquistion)
 * Default: 0
 */
//define LWIP_NETIF_STATUS_CALLBACK      0

/**
 * LWIP_NETIF_LINK_CALLBACK==1: Support a callback function from an interface
 * whenever the link changes (i.e., link down)
 * Default: 0
 */
//define LWIP_NETIF_LINK_CALLBACK        0

/**
 * LWIP_NETIF_REMOVE_CALLBACK==1: Support a callback function that is called
 * when a netif has been removed
 * Default: 0
 */
//define LWIP_NETIF_REMOVE_CALLBACK      0

/**
 * LWIP_NETIF_HWADDRHINT==1: Cache link-layer-address hints (e.g. table
 * indices) in struct netif. TCP and UDP can make use of this to prevent
 * scanning the ARP table for every sent packet. While this is faster for big
 * ARP tables or many concurrent connections, it might be counterproductive
 * if you have a tiny ARP table or if there never are concurrent connections.
 * Default: 0
 */
//define LWIP_NETIF_HWADDRHINT           0

/**
 * LWIP_NETIF_LOOPBACK==1: Support sending packets with a destination IP
 * address equal to the netif IP address, looping them back up the stack.
 * Default: 0
 */
//define LWIP_NETIF_LOOPBACK             0

/**
 * LWIP_LOOPBACK_MAX_PBUFS: Maximum number of pbufs on queue for loopback
 * sending for each netif (0 = disabled)
 * Default: 0
 */
//define LWIP_LOOPBACK_MAX_PBUFS         0

/**
 * LWIP_NETIF_LOOPBACK_MULTITHREADING: Indicates whether threading is enabled in
 * the system, as netifs must change how they behave depending on this setting
 * for the LWIP_NETIF_LOOPBACK option to work.
 * Setting this is needed to avoid reentering non-reentrant functions like
 * tcp_input().
 *    LWIP_NETIF_LOOPBACK_MULTITHREADING==1: Indicates that the user is using a
 *       multithreaded environment like tcpip.c. In this case, netif->input()
 *       is called directly.
 *    LWIP_NETIF_LOOPBACK_MULTITHREADING==0: Indicates a polling (or NO_SYS) setup.
 *       The packets are put on a list and netif_poll() must be called in
 *       the main application loop.
 * Default: (!NO_SYS)
 */
//define LWIP_NETIF_LOOPBACK_MULTITHREADING    (!NO_SYS)

/**
 * LWIP_NETIF_TX_SINGLE_PBUF: if this is set to 1, lwIP tries to put all data
 * to be sent into one single pbuf. This is for compatibility with DMA-enabled
 * MACs that do not support scatter-gather.
 * Beware that this might involve CPU-memcpy before transmitting that would not
 * be needed without this flag! Use this only if you need to!
 *
 * @todo: TCP and IP-frag do not work with this, yet:
 *
 * Default: 0
 */
//define LWIP_NETIF_TX_SINGLE_PBUF             0

/*
   ------------------------------------
   ---------- LOOPIF options ----------
   ------------------------------------
*/
/**
 * LWIP_HAVE_LOOPIF==1: Support loop interface (127.0.0.1) and loopif.c
 * Default: 0
 */
//define LWIP_HAVE_LOOPIF                0

/*
   ------------------------------------
   ---------- SLIPIF options ----------
   ------------------------------------
*/
/**
 * LWIP_HAVE_SLIPIF==1: Support slip interface and slipif.c
 * Default: 0
 */
//define LWIP_HAVE_SLIPIF                0

/*
   ------------------------------------
   ---------- Thread options ----------
   ------------------------------------
*/
/**
 * TCPIP_THREAD_NAME: The name assigned to the main tcpip thread.
 * Default: "tcpip_thread"
 */
//define TCPIP_THREAD_NAME              "tcpip_thread"

/**
 * TCPIP_THREAD_STACKSIZE: The stack size used by the main tcpip thread.
 * The stack size value itself is platform-dependent, but is passed to
 * sys_thread_new() when the thread is created.
 * Default: 0
 */
//define TCPIP_THREAD_STACKSIZE          0

/**
 * TCPIP_THREAD_PRIO: The priority assigned to the main tcpip thread.
 * The priority value itself is platform-dependent, but is passed to
 * sys_thread_new() when the thread is created.
 * Default: 1
 */
//define TCPIP_THREAD_PRIO               1

/**
 * TCPIP_MBOX_SIZE: The mailbox size for the tcpip thread messages
 * The queue size value itself is platform-dependent, but is passed to
 * sys_mbox_new() when tcpip_init is called.
 * Default: 0
 */
//define TCPIP_MBOX_SIZE                 0

/**
 * SLIPIF_THREAD_NAME: The name assigned to the slipif_loop thread.
 * Default: "slipif_loop"
 */
//define SLIPIF_THREAD_NAME             "slipif_loop"

/**
 * SLIP_THREAD_STACKSIZE: The stack size used by the slipif_loop thread.
 * The stack size value itself is platform-dependent, but is passed to
 * sys_thread_new() when the thread is created.
 * Default: 0
 */
//define SLIPIF_THREAD_STACKSIZE         0

/**
 * SLIPIF_THREAD_PRIO: The priority assigned to the slipif_loop thread.
 * The priority value itself is platform-dependent, but is passed to
 * sys_thread_new() when the thread is created.
 * Default: 1
 */
//define SLIPIF_THREAD_PRIO              1

/**
 * PPP_THREAD_NAME: The name assigned to the pppInputThread.
 * Default: "pppInputThread"
 */
//define PPP_THREAD_NAME                "pppInputThread"

/**
 * PPP_THREAD_STACKSIZE: The stack size used by the pppInputThread.
 * The stack size value itself is platform-dependent, but is passed to
 * sys_thread_new() when the thread is created.
 * Default: 0
 */
//define PPP_THREAD_STACKSIZE            0

/**
 * PPP_THREAD_PRIO: The priority assigned to the pppInputThread.
 * The priority value itself is platform-dependent, but is passed to
 * sys_thread_new() when the thread is created.
 * Default: 1
 */
//define PPP_THREAD_PRIO                 1

/**
 * DEFAULT_THREAD_NAME: The name assigned to any other lwIP thread.
 * Default: "lwIP"
 */
//define DEFAULT_THREAD_NAME            "lwIP"

/**
 * DEFAULT_THREAD_STACKSIZE: The stack size used by any other lwIP thread.
 * The stack size value itself is platform-dependent, but is passed to
 * sys_thread_new() when the thread is created.
 * Default: 0
 */
//define DEFAULT_THREAD_STACKSIZE        0

/**
 * DEFAULT_THREAD_PRIO: The priority assigned to any other lwIP thread.
 * The priority value itself is platform-dependent, but is passed to
 * sys_thread_new() when the thread is created.
 * Default: 1
 */
//define DEFAULT_THREAD_PRIO             1

/**
 * DEFAULT_RAW_RECVMBOX_SIZE: The mailbox size for the incoming packets on a
 * NETCONN_RAW. The queue size value itself is platform-dependent, but is passed
 * to sys_mbox_new() when the recvmbox is created.
 * Default: 0
 */
//define DEFAULT_RAW_RECVMBOX_SIZE       0

/**
 * DEFAULT_UDP_RECVMBOX_SIZE: The mailbox size for the incoming packets on a
 * NETCONN_UDP. The queue size value itself is platform-dependent, but is passed
 * to sys_mbox_new() when the recvmbox is created.
 * Default: 0
 */
//define DEFAULT_UDP_RECVMBOX_SIZE       0

/**
 * DEFAULT_TCP_RECVMBOX_SIZE: The mailbox size for the incoming packets on a
 * NETCONN_TCP. The queue size value itself is platform-dependent, but is passed
 * to sys_mbox_new() when the recvmbox is created.
 * Default: 0
 */
//define DEFAULT_TCP_RECVMBOX_SIZE       0

/**
 * DEFAULT_ACCEPTMBOX_SIZE: The mailbox size for the incoming connections.
 * The queue size value itself is platform-dependent, but is passed to
 * sys_mbox_new() when the acceptmbox is created.
 * Default: 0
 */
//define DEFAULT_ACCEPTMBOX_SIZE         0

/*
   ----------------------------------------------
   ---------- Sequential layer options ----------
   ----------------------------------------------
*/
/**
 * LWIP_TCPIP_CORE_LOCKING: (EXPERIMENTAL!)
 * Don't use it if you're not an active lwIP project member
 * Default: 0
 */
//define LWIP_TCPIP_CORE_LOCKING         0

/**
 * LWIP_TCPIP_CORE_LOCKING_INPUT: (EXPERIMENTAL!)
 * Don't use it if you're not an active lwIP project member
 * Default: 0
 */
//define LWIP_TCPIP_CORE_LOCKING_INPUT   0

/**
 * LWIP_NETCONN==1: Enable Netconn API (require to use api_lib.c)
 * Default: 1
 */
//define LWIP_NETCONN                    1

/**
 * LWIP_TCPIP_TIMEOUT==1: Enable tcpip_timeout/tcpip_untimeout tod create
 * timers running in tcpip_thread from another thread.
 * Default: 1
 */
//define LWIP_TCPIP_TIMEOUT              1

/*
   ------------------------------------
   ---------- Socket options ----------
   ------------------------------------
*/
/**
 * LWIP_SOCKET==1: Enable Socket API (require to use sockets.c)
 * Default: 1
 */
//define LWIP_SOCKET                     1

/**
 * LWIP_COMPAT_SOCKETS==1: Enable BSD-style sockets functions names.
 * (only used if you use sockets.c)
 * Default: 1
 */
#define LWIP_COMPAT_SOCKETS             0

/**
 * LWIP_POSIX_SOCKETS_IO_NAMES==1: Enable POSIX-style sockets functions names.
 * Disable this option if you use a POSIX operating system that uses the same
 * names (read, write & close). (only used if you use sockets.c)
 * Default: 1
 */
#define LWIP_POSIX_SOCKETS_IO_NAMES     0

/**
 * LWIP_TCP_KEEPALIVE==1: Enable TCP_KEEPIDLE, TCP_KEEPINTVL and TCP_KEEPCNT
 * options processing. Note that TCP_KEEPIDLE and TCP_KEEPINTVL have to be set
 * in seconds. (does not require sockets.c, and will affect tcp.c)
 * Default: 0
 */
//define LWIP_TCP_KEEPALIVE              0

/**
 * LWIP_SO_SNDTIMEO==1: Enable send timeout for sockets/netconns and
 * SO_SNDTIMEO processing.
 * Default: 0
 */
//define LWIP_SO_SNDTIMEO                0

/**
 * LWIP_SO_RCVTIMEO==1: Enable receive timeout for sockets/netconns and
 * SO_RCVTIMEO processing.
 * Default: 0
 */
//define LWIP_SO_RCVTIMEO                0

/**
 * LWIP_SO_RCVBUF==1: Enable SO_RCVBUF processing.
 * Default: 0
 */
//define LWIP_SO_RCVBUF                  0

/**
 * If LWIP_SO_RCVBUF is used, this is the default value for recv_bufsize.
 * Default: INT_MAX
 */
//define RECV_BUFSIZE_DEFAULT            INT_MAX

/**
 * SO_REUSE==1: Enable SO_REUSEADDR option.
 * Default: 0
 */
//define SO_REUSE                        0

/**
 * SO_REUSE_RXTOALL==1: Pass a copy of incoming broadcast/multicast packets
 * to all local matches if SO_REUSEADDR is turned on.
 * WARNING: Adds a memcpy for every packet if passing to more than one pcb!
 * Default: 0
 */
//define SO_REUSE_RXTOALL                0

/*
   ----------------------------------------
   ---------- Statistics options ----------
   ----------------------------------------
*/
/**
 * LWIP_STATS==1: Enable statistics collection in lwip_stats.
 * Default: 1
 */
#define LWIP_STATS                      1

#if LWIP_STATS

/**
 * LWIP_STATS_DISPLAY==1: Compile in the statistics output functions.
 * Default: 0
 */
//define LWIP_STATS_DISPLAY              0

/**
 * LINK_STATS==1: Enable link stats.
 * Default: 1
 */
//define LINK_STATS                      1

/**
 * ETHARP_STATS==1: Enable etharp stats.
 * Default: (LWIP_ARP)
 */
//define ETHARP_STATS                    (LWIP_ARP)

/**
 * IP_STATS==1: Enable IP stats.
 * Default: 1
 */
//define IP_STATS                        1

/**
 * IPFRAG_STATS==1: Enable IP fragmentation stats. Default is
 * on if using either frag or reass.
 * Default: (IP_REASSEMBLY || IP_FRAG)
 */
//define IPFRAG_STATS                    (IP_REASSEMBLY || IP_FRAG)

/**
 * ICMP_STATS==1: Enable ICMP stats.
 * Default: 1
 */
//define ICMP_STATS                      1

/**
 * IGMP_STATS==1: Enable IGMP stats.
 * Default: (LWIP_IGMP)
 */
//define IGMP_STATS                      (LWIP_IGMP)

/**
 * UDP_STATS==1: Enable UDP stats. Default is on if
 * UDP enabled, otherwise off.
 * Default: (LWIP_UDP)
 */
//define UDP_STATS                       (LWIP_UDP)

/**
 * TCP_STATS==1: Enable TCP stats. Default is on if TCP
 * enabled, otherwise off.
 * Default: (LWIP_TCP)
 */
//define TCP_STATS                       (LWIP_TCP)

/**
 * MEM_STATS==1: Enable mem.c stats.
 * Default: ((MEM_LIBC_MALLOC == 0) && (MEM_USE_POOLS == 0))
 */
//define MEM_STATS                       ((MEM_LIBC_MALLOC == 0) && (MEM_USE_POOLS == 0))

/**
 * MEMP_STATS==1: Enable memp.c pool stats.
 * Default: (MEMP_MEM_MALLOC == 0)
 */
//define MEMP_STATS                      (MEMP_MEM_MALLOC == 0)

/**
 * SYS_STATS==1: Enable system stats (sem and mbox counts, etc).
 * Default: (NO_SYS == 0)
 */
//define SYS_STATS                       (NO_SYS == 0)

#else

#define LINK_STATS                      0
#define IP_STATS                        0
#define IPFRAG_STATS                    0
#define ICMP_STATS                      0
#define IGMP_STATS                      0
#define UDP_STATS                       0
#define TCP_STATS                       0
#define MEM_STATS                       0
#define MEMP_STATS                      0
#define SYS_STATS                       0
#define LWIP_STATS_DISPLAY              0

#endif /* LWIP_STATS */

/*
   ---------------------------------
   ---------- PPP options ----------
   ---------------------------------
*/
/**
 * PPP_SUPPORT==1: Enable PPP.
 * Default: 0
 */
//define PPP_SUPPORT                     0

/**
 * PPPOE_SUPPORT==1: Enable PPP Over Ethernet
 * Default: 0
 */
//define PPPOE_SUPPORT                   0

/**
 * PPPOS_SUPPORT==1: Enable PPP Over Serial
 * Default: PPP_SUPPORT
 */
//define PPPOS_SUPPORT                   PPP_SUPPORT

//if PPP_SUPPORT

/**
 * NUM_PPP: Max PPP sessions.
 * Default: 1
 */
//define NUM_PPP                         1

/**
 * PAP_SUPPORT==1: Support PAP.
 * Default: 0
 */
//define PAP_SUPPORT                     0

/**
 * CHAP_SUPPORT==1: Support CHAP.
 * Default: 0
 */
//define CHAP_SUPPORT                    0

/**
 * MSCHAP_SUPPORT==1: Support MSCHAP. CURRENTLY NOT SUPPORTED! DO NOT SET!
 * Default: 0
 */
//define MSCHAP_SUPPORT                  0

/**
 * CBCP_SUPPORT==1: Support CBCP. CURRENTLY NOT SUPPORTED! DO NOT SET!
 * Default: 0
 */
//define CBCP_SUPPORT                    0

/**
 * CCP_SUPPORT==1: Support CCP. CURRENTLY NOT SUPPORTED! DO NOT SET!
 * Default: 0
 */
//define CCP_SUPPORT                     0

/**
 * VJ_SUPPORT==1: Support VJ header compression.
 * Default: 0
 */
//define VJ_SUPPORT                      0

/**
 * MD5_SUPPORT==1: Support MD5 (see also CHAP).
 * Default: 0
 */
//define MD5_SUPPORT                     0

/*
 * Timeouts
 */
/**
 * Default: 6
 */
//define FSM_DEFTIMEOUT                  6       /* Timeout time in seconds */

/**
 * Default: 2
 */
//define FSM_DEFMAXTERMREQS              2       /* Maximum Terminate-Request transmissions */

/**
 * Default: 10
 */
//define FSM_DEFMAXCONFREQS              10      /* Maximum Configure-Request transmissions */

/**
 * Default: 5
 */
//define FSM_DEFMAXNAKLOOPS              5       /* Maximum number of nak loops */

/**
 * Defaut: 6
 */
//define UPAP_DEFTIMEOUT                 6       /* Timeout (seconds) for retransmitting req */

/**
 * Default: 30
 */
//define UPAP_DEFREQTIME                 30      /* Time to wait for auth-req from peer */

/**
 * Default: 6
 */
//define CHAP_DEFTIMEOUT                 6       /* Timeout time in seconds */

/**
 * Default: 10
 */
//define CHAP_DEFTRANSMITS               10      /* max # times to send challenge */

/**
 * Interval in seconds between keepalive echo requests, 0 to disable.
 * Default: 0
 */
//define LCP_ECHOINTERVAL                0

/**
 * Number of unanswered echo requests before failure.
 * Default: 3
 */
//define LCP_MAXECHOFAILS                3

/**
 * Max Xmit idle time (in jiffies) before resend flag char.
 * Default: 100
 */
//define PPP_MAXIDLEFLAG                 100

/*
 * Packet sizes
 *
 * Note - lcp shouldn't be allowed to negotiate stuff outside these
 *    limits.  See lcp.h in the pppd directory.
 * (XXX - these constants should simply be shared by lcp.c instead
 *    of living in lcp.h)
 */
/**
 * Default: 1500
 */
//define PPP_MTU                         1500     /* Default MTU (size of Info field) */

/**
 * Default: 1500
 */
//define PPP_MAXMTU                      1500 /* Largest MTU we allow */

/**
 * Default: 64
 */
//define PPP_MINMTU                      64

/**
 * Default: 1500
 */
//define PPP_MRU                         1500     /* default MRU = max length of info field */

/**
 * Default: 1500
 */
//define PPP_MAXMRU                      1500     /* Largest MRU we allow */

/**
 * Default: 296
 */
//define PPP_DEFMRU                      296             /* Try for this */

/**
 * Default: 128
 */
//define PPP_MINMRU                      128             /* No MRUs below this */

/**
 * Default: 256
 */
//define MAXNAMELEN                      256     /* max length of hostname or name for auth */

/**
 * Default: 256
 */
//define MAXSECRETLEN                    256     /* max length of password or secret */

//endif /* PPP_SUPPORT */

/*
   --------------------------------------
   ---------- Checksum options ----------
   --------------------------------------
*/
/**
 * CHECKSUM_GEN_IP==1: Generate checksums in software for outgoing IP packets.
 * Default: 1
 */
//define CHECKSUM_GEN_IP                 1

/**
 * CHECKSUM_GEN_UDP==1: Generate checksums in software for outgoing UDP packets.
 * Default: 1
 */
//define CHECKSUM_GEN_UDP                1

/**
 * CHECKSUM_GEN_TCP==1: Generate checksums in software for outgoing TCP packets.
 * Default: 1
 */
//define CHECKSUM_GEN_TCP                1

/**
 * CHECKSUM_GEN_ICMP==1: Generate checksums in software for outgoing ICMP packets.
 * Default: 1
 */
//define CHECKSUM_GEN_ICMP               1

/*
 * We don't check incoming packets checksum due to we got it via trusted channel
 */
/**
 * CHECKSUM_CHECK_IP==1: Check checksums in software for incoming IP packets.
 * Default: 1
 */
#define CHECKSUM_CHECK_IP               0

/**
 * CHECKSUM_CHECK_UDP==1: Check checksums in software for incoming UDP packets.
 * Default: 1
 */
#define CHECKSUM_CHECK_UDP              0

/**
 * CHECKSUM_CHECK_TCP==1: Check checksums in software for incoming TCP packets.
 * Default: 1
 */
#define CHECKSUM_CHECK_TCP              0

/**
 * LWIP_CHECKSUM_ON_COPY==1: Calculate checksum when copying data from
 * application buffers to pbufs.
 * Default: 0
 */
//define LWIP_CHECKSUM_ON_COPY           0

/*
   ---------------------------------------
   ---------- Hook options ---------------
   ---------------------------------------
*/

/* Hooks are undefined by default, define them to a function if you need them. */

/**
 * LWIP_HOOK_IP4_INPUT(pbuf, input_netif):
 * - called from ip_input() (IPv4)
 * - pbuf: received struct pbuf passed to ip_input()
 * - input_netif: struct netif on which the packet has been received
 * Return values:
 * - 0: Hook has not consumed the packet, packet is processed as normal
 * - != 0: Hook has consumed the packet.
 * If the hook consumed the packet, 'pbuf' is in the responsibility of the hook
 * (i.e. free it when done).
 */

/**
 * LWIP_HOOK_IP4_ROUTE(dest):
 * - called from ip_route() (IPv4)
 * - dest: destination IPv4 address
 * Returns the destination netif or NULL if no destination netif is found. In
 * that case, ip_route() continues as normal.
 */

/*
   ---------------------------------------
   ---------- Debugging options ----------
   ---------------------------------------
*/
/**
 * LWIP_DBG_MIN_LEVEL: After masking, the value of the debug is
 * compared against this value. If it is smaller, then debugging
 * messages are written.
 */
#ifndef LWIP_DBG_MIN_LEVEL
#define LWIP_DBG_MIN_LEVEL              LWIP_DBG_LEVEL_ALL
#endif

/**
 * LWIP_DBG_TYPES_ON: A mask that can be used to globally enable/disable
 * debug messages of certain types.
 */
#ifndef LWIP_DBG_TYPES_ON
#define LWIP_DBG_TYPES_ON               LWIP_DBG_ON
#endif

/**
 * ETHARP_DEBUG: Enable debugging in etharp.c.
 */
#ifndef ETHARP_DEBUG
#define ETHARP_DEBUG                    LWIP_DBG_OFF
#endif

/**
 * NETIF_DEBUG: Enable debugging in netif.c.
 */
#ifndef NETIF_DEBUG
#define NETIF_DEBUG                     LWIP_DBG_OFF
#endif

/**
 * PBUF_DEBUG: Enable debugging in pbuf.c.
 */
#ifndef PBUF_DEBUG
#define PBUF_DEBUG                      LWIP_DBG_OFF
#endif

/**
 * API_LIB_DEBUG: Enable debugging in api_lib.c.
 */
#ifndef API_LIB_DEBUG
#define API_LIB_DEBUG                   LWIP_DBG_OFF
#endif

/**
 * API_MSG_DEBUG: Enable debugging in api_msg.c.
 */
#ifndef API_MSG_DEBUG
#define API_MSG_DEBUG                   LWIP_DBG_OFF
#endif

/**
 * SOCKETS_DEBUG: Enable debugging in sockets.c.
 */
#ifndef SOCKETS_DEBUG
#define SOCKETS_DEBUG                   LWIP_DBG_OFF
#endif

/**
 * ICMP_DEBUG: Enable debugging in icmp.c.
 */
#ifndef ICMP_DEBUG
#define ICMP_DEBUG                      LWIP_DBG_OFF
#endif

/**
 * IGMP_DEBUG: Enable debugging in igmp.c.
 */
#ifndef IGMP_DEBUG
#define IGMP_DEBUG                      LWIP_DBG_OFF
#endif

/**
 * INET_DEBUG: Enable debugging in inet.c.
 */
#ifndef INET_DEBUG
#define INET_DEBUG                      LWIP_DBG_OFF
#endif

/**
 * IP_DEBUG: Enable debugging for IP.
 */
#ifndef IP_DEBUG
#define IP_DEBUG                        LWIP_DBG_OFF
#endif

/**
 * IP_REASS_DEBUG: Enable debugging in ip_frag.c for both frag & reass.
 */
#ifndef IP_REASS_DEBUG
#define IP_REASS_DEBUG                  LWIP_DBG_OFF
#endif

/**
 * RAW_DEBUG: Enable debugging in raw.c.
 */
#ifndef RAW_DEBUG
#define RAW_DEBUG                       LWIP_DBG_OFF
#endif

/**
 * MEM_DEBUG: Enable debugging in mem.c.
 */
#ifndef MEM_DEBUG
#define MEM_DEBUG                       LWIP_DBG_OFF
#endif

/**
 * MEMP_DEBUG: Enable debugging in memp.c.
 */
#ifndef MEMP_DEBUG
#define MEMP_DEBUG                      LWIP_DBG_OFF
#endif

/**
 * SYS_DEBUG: Enable debugging in sys.c.
 */
#ifndef SYS_DEBUG
#define SYS_DEBUG                       LWIP_DBG_OFF
#endif

/**
 * TIMERS_DEBUG: Enable debugging in timers.c.
 */
#ifndef TIMERS_DEBUG
#define TIMERS_DEBUG                    LWIP_DBG_OFF
#endif

/**
 * TCP_DEBUG: Enable debugging for TCP.
 */
#ifndef TCP_DEBUG
#define TCP_DEBUG                       LWIP_DBG_OFF
#endif

/**
 * TCP_INPUT_DEBUG: Enable debugging in tcp_in.c for incoming debug.
 */
#ifndef TCP_INPUT_DEBUG
#define TCP_INPUT_DEBUG                 LWIP_DBG_OFF
#endif

/**
 * TCP_FR_DEBUG: Enable debugging in tcp_in.c for fast retransmit.
 */
#ifndef TCP_FR_DEBUG
#define TCP_FR_DEBUG                    LWIP_DBG_OFF
#endif

/**
 * TCP_RTO_DEBUG: Enable debugging in TCP for retransmit
 * timeout.
 */
#ifndef TCP_RTO_DEBUG
#define TCP_RTO_DEBUG                   LWIP_DBG_OFF
#endif

/**
 * TCP_CWND_DEBUG: Enable debugging for TCP congestion window.
 */
#ifndef TCP_CWND_DEBUG
#define TCP_CWND_DEBUG                  LWIP_DBG_OFF
#endif

/**
 * TCP_WND_DEBUG: Enable debugging in tcp_in.c for window updating.
 */
#ifndef TCP_WND_DEBUG
#define TCP_WND_DEBUG                   LWIP_DBG_OFF
#endif

/**
 * TCP_OUTPUT_DEBUG: Enable debugging in tcp_out.c output functions.
 */
#ifndef TCP_OUTPUT_DEBUG
#define TCP_OUTPUT_DEBUG                LWIP_DBG_OFF
#endif

/**
 * TCP_RST_DEBUG: Enable debugging for TCP with the RST message.
 */
#ifndef TCP_RST_DEBUG
#define TCP_RST_DEBUG                   LWIP_DBG_OFF
#endif

/**
 * TCP_QLEN_DEBUG: Enable debugging for TCP queue lengths.
 */
#ifndef TCP_QLEN_DEBUG
#define TCP_QLEN_DEBUG                  LWIP_DBG_OFF
#endif

/**
 * UDP_DEBUG: Enable debugging in UDP.
 */
#ifndef UDP_DEBUG
#define UDP_DEBUG                       LWIP_DBG_OFF
#endif

/**
 * TCPIP_DEBUG: Enable debugging in tcpip.c.
 */
#ifndef TCPIP_DEBUG
#define TCPIP_DEBUG                     LWIP_DBG_OFF
#endif

/**
 * PPP_DEBUG: Enable debugging for PPP.
 */
#ifndef PPP_DEBUG
#define PPP_DEBUG                       LWIP_DBG_OFF
#endif

/**
 * SLIP_DEBUG: Enable debugging in slipif.c.
 */
#ifndef SLIP_DEBUG
#define SLIP_DEBUG                      LWIP_DBG_OFF
#endif

/**
 * DHCP_DEBUG: Enable debugging in dhcp.c.
 */
#ifndef DHCP_DEBUG
#define DHCP_DEBUG                      LWIP_DBG_OFF
#endif

/**
 * AUTOIP_DEBUG: Enable debugging in autoip.c.
 */
#ifndef AUTOIP_DEBUG
#define AUTOIP_DEBUG                    LWIP_DBG_OFF
#endif

/**
 * SNMP_MSG_DEBUG: Enable debugging for SNMP messages.
 */
#ifndef SNMP_MSG_DEBUG
#define SNMP_MSG_DEBUG                  LWIP_DBG_OFF
#endif

/**
 * SNMP_MIB_DEBUG: Enable debugging for SNMP MIBs.
 */
#ifndef SNMP_MIB_DEBUG
#define SNMP_MIB_DEBUG                  LWIP_DBG_OFF
#endif

/**
 * DNS_DEBUG: Enable debugging for DNS.
 */
#ifndef DNS_DEBUG
#define DNS_DEBUG                       LWIP_DBG_OFF
#endif


#endif /* LWIPOPTS_H_ */
