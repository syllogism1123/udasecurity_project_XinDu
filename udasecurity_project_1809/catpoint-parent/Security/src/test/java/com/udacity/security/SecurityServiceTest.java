package com.udacity.security;

import com.udacity.image.service.ImageService;
import com.udacity.security.application.StatusListener;
import com.udacity.security.data.*;
import com.udacity.security.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

    @Mock
    private ImageService imageService;
    @Mock
    private SecurityRepository securityRepository;

    private SecurityService securityService;

    private Sensor sensor;


    private Set<Sensor> setupSensors(int amount, boolean active) {
        Set<Sensor> sensors = new HashSet<>();
        for (int i = 0; i < amount; i++) {
            sensors.add(new Sensor(UUID.randomUUID().toString(), SensorType.DOOR));
        }
        sensors.forEach(s -> s.setActive(active));
        return sensors;
    }

    @BeforeEach
    void init() {
        securityService = new SecurityService(securityRepository, imageService);
        sensor = new Sensor(UUID.randomUUID().toString(), SensorType.DOOR); //construct with random UUIDName
    }

    /**
     * 1.If alarm is armed and a sensor becomes activated, put the system into pending alarm status.
     */

    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_AWAY", "ARMED_HOME"})
    void setAlarmStatus_alarmArmedSensorActivated_setIntoPending(ArmingStatus armingStatus) {
        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, times(1)).setAlarmStatus(eq(AlarmStatus.PENDING_ALARM));
    }

    /**
     * 2.If alarm is armed and a sensor becomes activated and the system is already pending alarm, set the alarm status to alarm.
     */

    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_AWAY", "ARMED_HOME"})
    void setAlarmStatus_alarmArmedSensorActivatedAlarmStatusPending_setIntoAlarm(ArmingStatus armingStatus) {
        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, times(1)).setAlarmStatus(eq(AlarmStatus.ALARM));
    }

    /**
     * 3.If pending alarm and all sensors are inactive, return to no alarm state.
     */

    @Test
    void setAlarmStatus_alarmPendingAllSensorsInactive_returnNoAlarmState() {
        sensor.setActive(true);
        when(securityService.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository, times(1)).setAlarmStatus(eq(AlarmStatus.NO_ALARM));

    }

    /**
     * 4.If alarm is active, change in sensor state should not affect the alarm state.
     */

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void sensorStateChange_activeAlarm_alarmStatusStayTheSame(boolean sensorState) {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor, sensorState);
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    /**
     * 5.If a sensor is activated while already active and the system is in pending state, change it to alarm state.
     */

    @Test
    void changeAlarmStatus_alarmPendingAndSensorActivated_returnAlarmState() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, times(1)).setAlarmStatus(eq(AlarmStatus.ALARM));
    }

    /**
     * 6.If a sensor is deactivated while already inactive, make no changes to the alarm state.
     */

    @ParameterizedTest
    @EnumSource(AlarmStatus.class)
    void changeSensorDeactivated_inactiveSensor_alarmStatusStayNotChanged(AlarmStatus alarmStatus) {
        when(securityRepository.getAlarmStatus()).thenReturn(alarmStatus);
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository, never()).setAlarmStatus(eq(alarmStatus));
    }

    /**
     * 7.If the image service identifies an image containing a cat while the system is armed-home,
     * put the system into alarm status.
     */

    @Test
    void changeAlarmStatus_CatDetectedWhileArmedHome_returnAlarmState() {

        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(BufferedImage.class), any(float.class))).thenReturn(true);
        securityService.processImage(mock(BufferedImage.class));
        verify(securityRepository, atMostOnce()).setAlarmStatus(eq(AlarmStatus.ALARM));

    }

    /**
     * 8.If the image service identifies an image that does not contain a cat,
     * change the status to no alarm as long as the sensors are not active.
     */

    @Test
    void changeAlarmStatus_catNotDetectedAndInactiveSensor_returnNoAlarmState() {
        Set<Sensor> sensors = setupSensors(2, true);
        sensors.forEach(s -> s.setActive(false));
        when(securityRepository.getSensors()).thenReturn(sensors);
        when(imageService.imageContainsCat(any(BufferedImage.class), any(float.class))).thenReturn(false);
        securityService.processImage(mock(BufferedImage.class));
        verify(securityRepository, times(1)).setAlarmStatus(eq(AlarmStatus.NO_ALARM));

    }

    /**
     * 9.If the system is disarmed, set the status to no alarm.
     */


    @Test
    void changeAlarmStatus_systemDisarmed_setIntoNoAlarmState() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        verify(securityRepository, times(1)).setAlarmStatus(eq(AlarmStatus.NO_ALARM));
    }

    /**
     * 10.If the system is armed, reset all sensors to inactive.
     */

    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_AWAY", "ARMED_HOME"})
    void resetAllSensors_systemArmed_setIntoInactive(ArmingStatus armingStatus) {
        Set<Sensor> sensors = setupSensors(4, true);
        when(securityRepository.getSensors()).thenReturn(sensors);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.setArmingStatus(armingStatus);
        securityService.getSensors().forEach(s -> assertFalse(s.getActive()));
    }


    /**
     * 11.If the system is armed-home while the camera shows a cat, set the alarm status to alarm.
     */

    @Test
    void changeAlarmStatus_ArmedHomeWhileCatDetected_returnAlarmState() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(BufferedImage.class), any(float.class))).thenReturn(true);
        securityService.processImage(mock(BufferedImage.class));
        verify(securityRepository, times(1)).setAlarmStatus(eq(AlarmStatus.ALARM));
    }

    /**
     * 1. Missing test1
     */

    @Test
    void addAndRemoveStatusListener() {
        securityService.addStatusListener(mock(StatusListener.class));
        securityService.removeStatusListener(mock(StatusListener.class));
    }

    /**
     * 2. Missing test2
     */
    @Test
    void addAndRemoveSensor() {
        securityService.addSensor(sensor);
        securityService.removeSensor(sensor);
    }

    /**
     * 3. Missing test3 If system is disarmed, a sensor activation do not affect alarm status.
     */
    @Test
    void sensorActivated_systemDisarmed_alarmStatusNotChanged() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }


    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_AWAY", "ARMED_HOME"})
    void changeArmingStatus_systemDisarmedWhileCatDetected_returnAlarmState(ArmingStatus armingStatus) {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        when(imageService.imageContainsCat(any(BufferedImage.class), any(float.class))).thenReturn(true);
        securityService.processImage(mock(BufferedImage.class));
        securityService.setArmingStatus(armingStatus);
        verify(securityRepository, times(1)).setAlarmStatus(eq(AlarmStatus.ALARM));
    }


}



