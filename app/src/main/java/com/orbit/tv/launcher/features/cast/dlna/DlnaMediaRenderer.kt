package com.orbit.tv.launcher.features.cast.dlna

import android.content.Context
import android.media.AudioManager
import android.os.Build

class DlnaMediaRenderer(
    private val context: Context,
    private val httpPort: Int = 8080,
    private val uuid: String = SsdpServer.generateDeviceUuid(context),
    private val onPlayRequested: (url: String) -> Unit
) {
    var currentUri: String = ""
        private set
    var isPlaying: Boolean = false
        private set

    val friendlyName: String
        get() {
            val model = Build.MODEL ?: "TV"
            return if (model.contains("TV", ignoreCase = true)) model else "Smart TV ($model)"
        }

    fun handleGet(path: String): Pair<String, String>? {
        return when (path) {
            "/upnp/description.xml" -> Pair("text/xml; charset=utf-8", buildDeviceDescriptionXml())
            "/upnp/avtransport.xml" -> Pair("text/xml; charset=utf-8", buildAvTransportScpdXml())
            "/upnp/renderingcontrol.xml" -> Pair("text/xml; charset=utf-8", buildRenderingControlScpdXml())
            "/upnp/connectionmanager.xml" -> Pair("text/xml; charset=utf-8", buildConnectionManagerScpdXml())
            else -> null
        }
    }

    fun handleSoapPost(path: String, soapAction: String, body: String): String {
        return when {
            path.contains("avtransport", ignoreCase = true) -> handleAvTransportSoap(soapAction, body)
            path.contains("renderingcontrol", ignoreCase = true) -> handleRenderingControlSoap(soapAction, body)
            path.contains("connectionmanager", ignoreCase = true) -> handleConnectionManagerSoap(soapAction, body)
            else -> buildSoapErrorResponse("401", "Invalid Action")
        }
    }

    private fun handleAvTransportSoap(soapAction: String, body: String): String {
        return when {
            soapAction.contains("SetAVTransportURI") || body.contains("SetAVTransportURI") -> {
                val uri = extractXmlTag(body, "CurrentURI")
                if (uri.isNotBlank()) {
                    currentUri = unescapeXml(uri)
                }
                buildSoapResponse("SetAVTransportURIResponse", "urn:schemas-upnp-org:service:AVTransport:1", "")
            }
            soapAction.contains("Play") || body.contains("Play") -> {
                isPlaying = true
                if (currentUri.isNotBlank()) {
                    onPlayRequested(currentUri)
                }
                buildSoapResponse("PlayResponse", "urn:schemas-upnp-org:service:AVTransport:1", "")
            }
            soapAction.contains("Stop") || body.contains("Stop") -> {
                isPlaying = false
                buildSoapResponse("StopResponse", "urn:schemas-upnp-org:service:AVTransport:1", "")
            }
            soapAction.contains("Pause") || body.contains("Pause") -> {
                isPlaying = false
                buildSoapResponse("PauseResponse", "urn:schemas-upnp-org:service:AVTransport:1", "")
            }
            soapAction.contains("GetTransportInfo") || body.contains("GetTransportInfo") -> {
                val state = if (isPlaying) "PLAYING" else "STOPPED"
                val inner = "<CurrentTransportState>$state</CurrentTransportState>" +
                        "<CurrentTransportStatus>OK</CurrentTransportStatus>" +
                        "<CurrentSpeed>1</CurrentSpeed>"
                buildSoapResponse("GetTransportInfoResponse", "urn:schemas-upnp-org:service:AVTransport:1", inner)
            }
            soapAction.contains("GetPositionInfo") || body.contains("GetPositionInfo") -> {
                val inner = "<Track>1</Track>" +
                        "<TrackDuration>00:00:00</TrackDuration>" +
                        "<TrackMetaData></TrackMetaData>" +
                        "<TrackURI>${escapeXml(currentUri)}</TrackURI>" +
                        "<RelTime>00:00:00</RelTime>" +
                        "<AbsTime>00:00:00</AbsTime>" +
                        "<RelCount>2147483647</RelCount>" +
                        "<AbsCount>2147483647</AbsCount>"
                buildSoapResponse("GetPositionInfoResponse", "urn:schemas-upnp-org:service:AVTransport:1", inner)
            }
            else -> {
                buildSoapResponse("DefaultResponse", "urn:schemas-upnp-org:service:AVTransport:1", "")
            }
        }
    }

    private fun handleRenderingControlSoap(soapAction: String, body: String): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

        return when {
            soapAction.contains("SetVolume") || body.contains("SetVolume") -> {
                val volStr = extractXmlTag(body, "DesiredVolume")
                val volPercent = volStr.toIntOrNull() ?: 50
                val target = (volPercent * maxVolume) / 100
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
                buildSoapResponse("SetVolumeResponse", "urn:schemas-upnp-org:service:RenderingControl:1", "")
            }
            soapAction.contains("GetVolume") || body.contains("GetVolume") -> {
                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val percent = (currentVol * 100) / maxVolume
                val inner = "<CurrentVolume>$percent</CurrentVolume>"
                buildSoapResponse("GetVolumeResponse", "urn:schemas-upnp-org:service:RenderingControl:1", inner)
            }
            soapAction.contains("SetMute") || body.contains("SetMute") -> {
                val muteStr = extractXmlTag(body, "DesiredMute")
                val isMute = muteStr == "1" || muteStr.equals("true", ignoreCase = true)
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    if (isMute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                    AudioManager.FLAG_SHOW_UI
                )
                buildSoapResponse("SetMuteResponse", "urn:schemas-upnp-org:service:RenderingControl:1", "")
            }
            soapAction.contains("GetMute") || body.contains("GetMute") -> {
                val inner = "<CurrentMute>0</CurrentMute>"
                buildSoapResponse("GetMuteResponse", "urn:schemas-upnp-org:service:RenderingControl:1", inner)
            }
            else -> {
                buildSoapResponse("DefaultResponse", "urn:schemas-upnp-org:service:RenderingControl:1", "")
            }
        }
    }

    private fun handleConnectionManagerSoap(soapAction: String, body: String): String {
        return if (soapAction.contains("GetProtocolInfo") || body.contains("GetProtocolInfo")) {
            val inner = "<Source></Source>" +
                    "<Sink>http-get:*:video/*:*,http-get:*:audio/*:*,http-get:*:image/*:*</Sink>"
            buildSoapResponse("GetProtocolInfoResponse", "urn:schemas-upnp-org:service:ConnectionManager:1", inner)
        } else {
            buildSoapResponse("DefaultResponse", "urn:schemas-upnp-org:service:ConnectionManager:1", "")
        }
    }

    private fun buildDeviceDescriptionXml(): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:dlna="urn:schemas-dlna-org:device-1-0">
  <specVersion>
    <major>1</major>
    <minor>0</minor>
  </specVersion>
  <device>
    <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
    <friendlyName>$friendlyName</friendlyName>
    <manufacturer>Android</manufacturer>
    <modelName>Media Renderer</modelName>
    <modelNumber>1.0</modelNumber>
    <modelDescription>Wireless Media Display</modelDescription>
    <UDN>uuid:$uuid</UDN>
    <dlna:X_DLNACAP/>
    <dlna:X_DLNADOC xmlns:dlna="urn:schemas-dlna-org:device-1-0">DMR-1.50</dlna:X_DLNADOC>
    <serviceList>
      <service>
        <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
        <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
        <SCPDURL>/upnp/avtransport.xml</SCPDURL>
        <controlURL>/upnp/control/avtransport</controlURL>
        <eventSubURL>/upnp/event/avtransport</eventSubURL>
      </service>
      <service>
        <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
        <serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>
        <SCPDURL>/upnp/renderingcontrol.xml</SCPDURL>
        <controlURL>/upnp/control/renderingcontrol</controlURL>
        <eventSubURL>/upnp/event/renderingcontrol</eventSubURL>
      </service>
      <service>
        <serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>
        <serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>
        <SCPDURL>/upnp/connectionmanager.xml</SCPDURL>
        <controlURL>/upnp/control/connectionmanager</controlURL>
        <eventSubURL>/upnp/event/connectionmanager</eventSubURL>
      </service>
    </serviceList>
  </device>
</root>"""
    }

    private fun buildAvTransportScpdXml(): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<scpd xmlns="urn:schemas-upnp-org:service-1-0">
  <specVersion><major>1</major><minor>0</minor></specVersion>
  <actionList>
    <action><name>SetAVTransportURI</name></action>
    <action><name>Play</name></action>
    <action><name>Stop</name></action>
    <action><name>Pause</name></action>
    <action><name>GetTransportInfo</name></action>
    <action><name>GetPositionInfo</name></action>
  </actionList>
  <serviceStateTable>
    <stateVariable sendEvents="no"><name>TransportState</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>CurrentURI</name><dataType>string</dataType></stateVariable>
  </serviceStateTable>
</scpd>"""
    }

    private fun buildRenderingControlScpdXml(): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<scpd xmlns="urn:schemas-upnp-org:service-1-0">
  <specVersion><major>1</major><minor>0</minor></specVersion>
  <actionList>
    <action><name>SetVolume</name></action>
    <action><name>GetVolume</name></action>
    <action><name>SetMute</name></action>
    <action><name>GetMute</name></action>
  </actionList>
  <serviceStateTable>
    <stateVariable sendEvents="no"><name>Volume</name><dataType>ui2</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>Mute</name><dataType>boolean</dataType></stateVariable>
  </serviceStateTable>
</scpd>"""
    }

    private fun buildConnectionManagerScpdXml(): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<scpd xmlns="urn:schemas-upnp-org:service-1-0">
  <specVersion><major>1</major><minor>0</minor></specVersion>
  <actionList>
    <action><name>GetProtocolInfo</name></action>
  </actionList>
  <serviceStateTable>
    <stateVariable sendEvents="no"><name>SourceProtocolInfo</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>SinkProtocolInfo</name><dataType>string</dataType></stateVariable>
  </serviceStateTable>
</scpd>"""
    }

    private fun buildSoapResponse(actionResponse: String, serviceUrn: String, innerXml: String): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:$actionResponse xmlns:u="$serviceUrn">
      $innerXml
    </u:$actionResponse>
  </s:Body>
</s:Envelope>"""
    }

    private fun buildSoapErrorResponse(errorCode: String, errorDesc: String): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
  <s:Body>
    <s:Fault>
      <faultcode>s:Client</faultcode>
      <faultstring>UPnPError</faultstring>
      <detail>
        <UPnPError xmlns="urn:schemas-upnp-org:control-1-0">
          <errorCode>$errorCode</errorCode>
          <errorDescription>$errorDesc</errorDescription>
        </UPnPError>
      </detail>
    </s:Fault>
  </s:Body>
</s:Envelope>"""
    }

    private fun extractXmlTag(xml: String, tag: String): String {
        val open = "<$tag"
        val close = "</$tag>"
        val startIdx = xml.indexOf(open)
        if (startIdx == -1) return ""
        val contentStart = xml.indexOf('>', startIdx) + 1
        val endIdx = xml.indexOf(close, contentStart)
        if (endIdx == -1) return ""
        return xml.substring(contentStart, endIdx).trim()
    }

    private fun unescapeXml(text: String): String {
        return text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
