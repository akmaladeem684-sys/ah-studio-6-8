#ifndef AH_ENGINE_REGISTRY_H
#define AH_ENGINE_REGISTRY_H
#include <cstddef>
#include <memory>
#include <unordered_map>
namespace ah_engine { template<class Engine> class EngineRegistry { public: using Handle=const void*; Engine* find(Handle h)const{auto i=map_.find(h);return i==map_.end()?nullptr:i->second.get();} Engine* findOrCreate(Handle h){if(!h)return nullptr;auto&s=map_[h];if(!s)s.reset(new Engine());return s.get();} std::unique_ptr<Engine> take(Handle h){auto i=map_.find(h);if(i==map_.end())return nullptr;auto e=std::move(i->second);map_.erase(i);return e;} std::size_t size()const{return map_.size();} private:std::unordered_map<Handle,std::unique_ptr<Engine>>map_;}; }
#endif