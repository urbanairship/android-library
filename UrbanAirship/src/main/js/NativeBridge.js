if (typeof UAirship === 'undefined') {

    UAirship = (function() {
      var urbanAirship = {}
      // Prototyping does not work for native interface objects and bugs exist
      // that prevent Object.keys() from working, so we are working around the
      // problem by explicitly listing the methods from the native interface
      // and adding methods to our urbanAirship object that wraps the methods.

      var methods = [
          'getDeviceModel'
        , 'getMessageId'
        , 'getMessageTitle'
        , 'getMessageSentDate'
        , 'getMessageSentDateMS'
        , 'getUserId'
        , 'close'
        , 'getViewHeight'
        , 'getViewWidth'
        , 'getDeviceOrientation'
        , 'navigateTo'
        , 'isMessageRead'
        , 'markMessageRead'
        , 'markMessageUnread'
      ]

      methods.forEach(function(name) {
        urbanAirship[name] = function() {
          var val = _UAirship[name].apply(_UAirship, arguments)

          return typeof val === 'undefined' ?  null : val
        }
      })

      var actionCallbacks = {}
        , callbackID = 0

      urbanAirship.runAction = function(actionName, argument, callback) {
        var callbackKey = 'ua-cb-' + (++callbackID)
          , argPayload

        actionCallbacks[callbackKey] = function(err, data) {
          if(err) {
            return callback(err)
          }

          try {
            if(data) {
              data = JSON.parse(data).value
            }
          } catch(err) {
            return callback(new Error('could not decode response: ' + data))
          }

          callback(null, data)
        }

        argPayload = {value: argument}

        _UAirship.actionCall(actionName, JSON.stringify(argPayload), callbackKey)
      }

      urbanAirship.finishAction = function(err, data, callbackKey) {
        if(actionCallbacks[callbackKey]) {
          actionCallbacks[callbackKey](err, data)
          delete actionCallbacks[callbackKey]
        }
      }

      return urbanAirship
    })()

    // Fire the ready event
    var uaLibraryReadyEvent = document.createEvent('Event')

    uaLibraryReadyEvent.initEvent('ualibraryready', true, true)
    document.dispatchEvent(uaLibraryReadyEvent)

    UAirship.isReady = true
}
