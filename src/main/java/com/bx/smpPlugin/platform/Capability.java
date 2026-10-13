package com.bx.smpPlugin.platform;

/**
 * One line of the platform report.
 *
 * @param name     what is being reported, e.g. "ProtocolLib" or "Native anti-Xray"
 * @param status   availability on this server
 * @param detail   human-readable explanation, including which features are affected
 */
public record Capability(String name, CapabilityStatus status, String detail) {
}
