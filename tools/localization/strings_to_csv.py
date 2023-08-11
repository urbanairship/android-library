#!/usr/bin/env python3

import csv
from lxml import etree

modules = [
    'core', 'message-center', 'preference-center'
]

languages = [
    'af', 'am', 'ar', 'bg', 'ca', 'cs', 'da', 'de', 'el', 'es', 'es-rES', 'et', 'fa', 'fi', 'fr', 'fr-rCA', 'hi', 'hr',
    'hu', 'in', 'it', 'iw', 'ja', 'ko', 'lt', 'lv', 'ms', 'nl', 'no', 'pl', 'pt', 'pt-rPT', 'ro', 'ru', 'sk', 'sl',
    'sr', 'sv', 'sw', 'th', 'tr', 'uk', 'vi', 'zh-rCN', 'zh-rHK', 'zh-rTW', 'zu'
]

def main():
    print("Starting...")
    for module in modules:
        strings = handle_module(module)
        dump_to_csv(f'{module}.csv', strings)
    print("Done!")

def handle_module(module):
    print(f"Dumping strings for module: {module}")
    res = module_res_path(module)

    en_strings = parse_strings_xml(strings_xml_path(res, None))

    module_strings = {k: {"en": v} for k, v in en_strings.items()}

    for lang in languages:
        lang_strings = parse_strings_xml(strings_xml_path(res, lang))
        for k, v in lang_strings.items():
          module_strings[k][lang] = v

    return module_strings

def module_res_path(module):
    return f'../../urbanairship-{module}/src/main/res'

def strings_xml_path(res_dir, lang = None):
    values_dir = f'values-{lang}' if lang else 'values'
    return f'{res_dir}/{values_dir}/strings.xml'

def parse_strings_xml(path):
    tree = etree.parse(path, parser=etree.XMLParser(encoding='utf-8'))
    root = tree.getroot()

    return {
        child.attrib['name']: child.text
        for child in root
        if isinstance(child.tag, str) and child.tag == 'string'
    }

def dump_to_csv(filename, strings):
    with open(filename, 'w', newline='') as out_file:
        out = csv.writer(out_file, delimiter=',', quotechar='"', quoting=csv.QUOTE_ALL)

        out.writerow(['string_id', 'en'] + languages)
        for k, v in strings.items():
            out.writerow([k, v['en']] + [v.get(lang, '') for lang in languages])


if __name__ == "__main__":
    main()
