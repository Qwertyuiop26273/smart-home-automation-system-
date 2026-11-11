package com.example

import com.example.bluetooth.ProtocolParser
import com.example.model.ApplianceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Bluetooth ASCII serial protocol parser and command generator.
 */
class ExampleUnitTest {

    @Test
    fun testBuildSetRelayCommands() {
        assertEquals("SET,FAN,1\n", ProtocolParser.buildSetRelayCommand(ApplianceType.FAN, true))
        assertEquals("SET,FAN,0\n", ProtocolParser.buildSetRelayCommand(ApplianceType.FAN, false))
        assertEquals("SET,AC,1\n", ProtocolParser.buildSetRelayCommand(ApplianceType.AC, true))
        assertEquals("SET,LAMP,1\n", ProtocolParser.buildSetRelayCommand(ApplianceType.LAMP, true))
        assertEquals("SET,LED,0\n", ProtocolParser.buildSetRelayCommand(ApplianceType.LED, false))
    }

    @Test
    fun testBuildStatusQueryCommand() {
        assertEquals("STATUS\n", ProtocolParser.buildStatusQueryCommand())
    }

    @Test
    fun testParseReady() {
        val result = ProtocolParser.parseLine("READY\r\n")
        assertTrue(result is ProtocolParser.ParsedResponse.Ready)
    }

    @Test
    fun testParseState() {
        val fanOn = ProtocolParser.parseLine("STATE,FAN,1\n")
        assertTrue(fanOn is ProtocolParser.ParsedResponse.State)
        fanOn as ProtocolParser.ParsedResponse.State
        assertEquals(ApplianceType.FAN, fanOn.appliance)
        assertTrue(fanOn.isOn)

        val acOff = ProtocolParser.parseLine("STATE,AC,0\r\n")
        assertTrue(acOff is ProtocolParser.ParsedResponse.State)
        acOff as ProtocolParser.ParsedResponse.State
        assertEquals(ApplianceType.AC, acOff.appliance)
        assertFalse(acOff.isOn)
    }

    @Test
    fun testParseSensor() {
        val sensor = ProtocolParser.parseLine("SENSOR,28.5,62.0\n")
        assertTrue(sensor is ProtocolParser.ParsedResponse.Sensor)
        sensor as ProtocolParser.ParsedResponse.Sensor
        assertFalse(sensor.data.isError)
        assertEquals(28.5f, sensor.data.temperatureCelsius ?: 0f, 0.01f)
        assertEquals(62.0f, sensor.data.humidityPercent ?: 0f, 0.01f)
    }

    @Test
    fun testParseErrors() {
        val dhtError = ProtocolParser.parseLine("ERROR,DHT_READ\n")
        assertTrue(dhtError is ProtocolParser.ParsedResponse.Error)
        dhtError as ProtocolParser.ParsedResponse.Error
        assertEquals("DHT_READ", dhtError.code)

        val badCmd = ProtocolParser.parseLine("ERROR,BAD_COMMAND\n")
        assertTrue(badCmd is ProtocolParser.ParsedResponse.Error)
        badCmd as ProtocolParser.ParsedResponse.Error
        assertEquals("BAD_COMMAND", badCmd.code)
    }
}
