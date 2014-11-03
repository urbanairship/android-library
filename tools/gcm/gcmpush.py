#!/usr/bin/python

import httplib, urllib
import uuid
import json
import sys
from optparse import OptionParser

GCM_URI = "https://android.googleapis.com/gcm/send"
GCM_SERVER = "android.googleapis.com"

def parse_args():
    parser = OptionParser(usage = "Usage: %prog file")
    parser.add_option("-n",
                      type=int,
                      dest="num_pushes",
                      default=1,
                      help="number of pushes to send")
    (options, args) = parser.parse_args()
    if len(args) < 1:
        parser.error("Missing input file")
    return (options, args)

def parse_push_data(filename):
    file = open(filename, 'r')
    try:
        push_data = json.loads(file.read())
    except ValueError:
        sys.exit("Error: Bad JSON")
    except IOError:
        sys.exit("Error: No such file or directory")
    file.close()
    if 'api-key' in push_data and 'alert' in push_data and 'recipient' in push_data:
        return push_data
    else:
        sys.exit("Error: Push data must contain api-key, alert and recipient key/value pairs")

def build_payload(push_data):
    payload_dict = {}
    data_dict = {"com.urbanairship.push.ALERT" : push_data['alert'], "com.urbanairship.push.PUSH_ID" : str(uuid.uuid4())}
    if 'extras' in push_data:
        extras = push_data['extras']
        for key in extras:
            data_dict[key] = extras[key]

    payload_dict["data"] = data_dict
    payload_dict["registration_ids"] = [push_data['recipient']]

    try:
        payload = json.dumps(payload_dict)
        return payload
    except TypeError:
        sys.exit("Error: Error serializing push payload")


def send_push(api_key, payload, num_pushes):
    headers = {"Content-type": "application/json", "Authorization": "key=%s" % api_key}

    for n in range(num_pushes):
        conn = httplib.HTTPSConnection(GCM_SERVER)
        conn.request("POST", "/gcm/send", payload, headers)

        response = conn.getresponse()
        print response.status, response.reason
        resp = response.read()

        try:
            data = json.loads(resp)
            if 'success' in data and 'failure' in data:
                if data['success'] > 0 and not data['failure']:
                    print("success")
                else:
                    print("failure")
            else:
                print("Error parsing JSON response from GCM")
        except ValueError:
            print("Error parsing JSON response from GCM")

        conn.close()

def main():
    options, args = parse_args()
    filename = args[0]
    push_data = parse_push_data(filename)

    api_key = push_data['api-key']
    recipient = push_data['recipient']
    print("sending push to recipient:%s" %recipient)

    payload = build_payload(push_data)
    send_push(api_key, payload, options.num_pushes)

if __name__ == '__main__':
    main()
