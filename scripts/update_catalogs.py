"""Rebuild versioned official GitHub catalogs. Requires PyYAML; never runs fetched code."""
import io, json, tarfile, urllib.request
from pathlib import Path
import yaml
ROOT = Path(__file__).resolve().parents[1] / 'app/src/main/assets/catalog'
ROOT.mkdir(parents=True, exist_ok=True)
def get(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent':'RepoRove-catalog-builder'}), timeout=60).read()
def snapshot(repo):
    meta=json.loads(get('https://api.github.com/repos/'+repo+'/commits/HEAD'))
    sha=meta['sha']
    return sha, tarfile.open(fileobj=io.BytesIO(get('https://codeload.github.com/'+repo+'/tar.gz/'+sha)),mode='r:gz')
def save(name,value):
    (ROOT/name).write_text(json.dumps(value,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
sha, archive=snapshot('github-linguist/linguist')
member=next(m for m in archive.getmembers() if m.name.endswith('/lib/linguist/languages.yml'))
langs=yaml.safe_load(archive.extractfile(member))
save('languages.json',{'source':'https://github.com/github-linguist/linguist','revision':sha,'items':[{'name':name,'aliases':v.get('aliases',[]),'color':v.get('color')} for name,v in langs.items()]})
license_file=next(m for m in archive.getmembers() if m.name.endswith('/LICENSE'))
(ROOT/'LINGUIST-LICENSE.txt').write_bytes(archive.extractfile(license_file).read())
sha,archive=snapshot('github/explore')
topics=[];collections=[]
for member in archive.getmembers():
    parts=member.name.split('/')
    if len(parts)!=4 or parts[-1]!='index.md' or parts[1] not in ['topics','collections']: continue
    raw=archive.extractfile(member).read().decode('utf-8')
    if not raw.startswith('---'): continue
    _,head,body=raw.split('---',2)
    v=yaml.safe_load(head) or {};slug=parts[2]
    item={'slug':slug,'name':v.get('display_name',slug),'description':v.get('short_description',body.strip()),'body':body.strip()}
    if parts[1]=='topics':
        item.update(aliases=[s.strip() for s in str(v.get('aliases','')).split(',') if s.strip()],logo='https://raw.githubusercontent.com/github/explore/'+sha+'/topics/'+slug+'/'+v['logo'] if v.get('logo') else None)
        topics.append(item)
    else:
        item['items']=[str(x) if not isinstance(x,dict) else x for x in v.get('items',[])]
        collections.append(item)
save('explore.json',{'source':'https://github.com/github/explore','revision':sha,'topics':sorted(topics,key=lambda x:x['name'].lower()),'collections':sorted(collections,key=lambda x:x['name'].lower())})
for m in archive.getmembers():
    if len(m.name.split('/'))==2 and m.name.split('/')[-1].startswith('LICENSE'):
        (ROOT/'EXPLORE-LICENSE.txt').write_bytes(archive.extractfile(m).read())
print('Generated',len(langs),'languages,',len(topics),'topics,',len(collections),'collections')
