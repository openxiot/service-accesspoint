package cc.openxiot.common.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrivateNetworkFilterTest {

    @Test
    void isPrivateNetwork_loopback() {
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("127.0.0.1"));
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("127.0.0.0"));
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("127.255.255.255"));
    }

    @Test
    void isPrivateNetwork_10Range() {
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("10.0.0.0"));
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("10.0.0.1"));
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("10.255.255.255"));
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("10.1.2.3"));
    }

    @Test
    void isPrivateNetwork_17216to17231() {
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("172.16.0.0"));
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("172.16.0.1"));
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("172.31.255.255"));
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("172.20.0.1"));

        // 172.32.0.0 is NOT in the 172.16.0.0/12 range
        assertFalse(PrivateNetworkFilter.isPrivateNetwork("172.32.0.0"));
        // 172.15.0.0 is NOT in the range
        assertFalse(PrivateNetworkFilter.isPrivateNetwork("172.15.0.0"));
    }

    @Test
    void isPrivateNetwork_192168() {
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("192.168.0.0"));
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("192.168.0.1"));
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("192.168.255.255"));
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("192.168.1.100"));

        assertFalse(PrivateNetworkFilter.isPrivateNetwork("192.169.0.0"));
        assertFalse(PrivateNetworkFilter.isPrivateNetwork("192.167.0.0"));
    }

    @Test
    void isPrivateNetwork_linkLocal() {
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("169.254.0.0"));
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("169.254.0.1"));
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("169.254.255.255"));
    }

    @Test
    void isPrivateNetwork_carrierGradeNat() {
        // 100.64.0.0/10
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("100.64.0.0"));
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("100.64.0.1"));
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("100.127.255.255"));
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("100.100.0.1"));

        // 100.128.0.0 is outside the range
        assertFalse(PrivateNetworkFilter.isPrivateNetwork("100.128.0.0"));
        // 100.63.0.0 is outside
        assertFalse(PrivateNetworkFilter.isPrivateNetwork("100.63.0.0"));
    }

    @Test
    void isPrivateNetwork_benchmark() {
        // 198.18.0.0/15
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("198.18.0.0"));
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("198.18.0.1"));
        assertTrue(PrivateNetworkFilter.isPrivateNetwork("198.19.255.255"));

        // 198.20.0.0 is outside
        assertFalse(PrivateNetworkFilter.isPrivateNetwork("198.20.0.0"));
        // 198.17.0.0 is outside
        assertFalse(PrivateNetworkFilter.isPrivateNetwork("198.17.0.0"));
    }

    @Test
    void isPrivateNetwork_publicIp_returnsFalse() {
        assertFalse(PrivateNetworkFilter.isPrivateNetwork("8.8.8.8"));
        assertFalse(PrivateNetworkFilter.isPrivateNetwork("114.114.114.114"));
        assertFalse(PrivateNetworkFilter.isPrivateNetwork("1.1.1.1"));
        assertFalse(PrivateNetworkFilter.isPrivateNetwork("220.181.38.148"));
    }

    @Test
    void isPrivateNetwork_ipv6_returnsFalse() {
        assertFalse(PrivateNetworkFilter.isPrivateNetwork("::1"));
        assertFalse(PrivateNetworkFilter.isPrivateNetwork("2001:4860:4860::8888"));
        assertFalse(PrivateNetworkFilter.isPrivateNetwork("fe80::1"));
    }

    @Test
    void isPrivateNetwork_undefinedLocalRange_returnsFalse() {
        // 1.0.0.0/8 is assigned to APNIC and is a valid public range
        assertFalse(PrivateNetworkFilter.isPrivateNetwork("1.1.1.1"));
        assertFalse(PrivateNetworkFilter.isPrivateNetwork("1.2.3.4"));
    }
}
