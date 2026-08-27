package com.gios.lightcontrol;

/**
 * The colour a sideloaded app asks BrightControl for, so it does not have to hold
 * WRITE_SECURE_SETTINGS itself.
 *
 * This file is the wire contract and it is byte-identical in BrightControl and in
 * light-common. An AIDL interface is identified by its descriptor, which is the package
 * and name written here, so the two copies must not drift: a renamed package or a
 * reordered method is a bind that succeeds and then throws.
 *
 * One bind is one hold. There is no explicit release, and that is deliberate: the death
 * of the connection is the release, which is the only signal that arrives whether the
 * client unbound tidily, was killed, or crashed.
 */
interface IColorProvider {

    /**
     * State what the caller wants on screen right now.
     *
     * state: 0 clears the request, 1 asks for colour, 2 asks for monochrome.
     *
     * Returns 1 when BrightControl is driving the screen and the caller must not write the
     * daltonizer itself, 0 when BrightControl is present but inert (no grant, or its colour
     * switch is off) and the caller should fall back to its own writer if it has one, and
     * -1 when the request was refused.
     *
     * The request is honoured only while the caller is the app in front. BrightControl
     * knows which app that is and takes the caller identity from the kernel, so a request
     * can only ever repaint a screen its sender is already occupying.
     */
    int want(int state);

    /**
     * Which revision of this contract the other side speaks. 1 is the first.
     *
     * Asked rather than assumed because the two APKs version separately and a phone will
     * routinely carry a new library against an old BrightControl. A client that needs
     * something added later can find out instead of calling a method that is not there.
     */
    int protocol();
}
